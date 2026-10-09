package dev.lowball.helper.client.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.CraftCost;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.UpgradeCategory;
import dev.lowball.helper.valuation.Valuation;

/**
 * The full "how was this valued" view: base price candidates, market, exotic info, every upgrade by category
 * (gemstones, enchants, stars…), crafting cost and the offer math. Built once into rows, drawn anywhere.
 */
public final class Breakdown {
	private static final int LINE = 10;

	private sealed interface Row permits Title, Section, Kv, Note, BarRow, Swatches, Gap {
	}

	private record Title(ItemStack stack, Component name, String sub, List<String[]> pills) implements Row {
	}

	private record Section(String title, String right, int accent) implements Row {
	}

	/** Label left, value right; indent levels of 8px. */
	private record Kv(String label, String value, int valueColor, int indent, boolean strong) implements Row {
	}

	private record Note(String text, int indent) implements Row {
	}

	private record BarRow(double fraction, int color, String left, String right) implements Row {
	}

	private record Swatches(int[] colors, String[] labels) implements Row {
	}

	private record Gap() implements Row {
	}

	private final List<Row> rows = new ArrayList<>();

	private Breakdown() {
	}

	public static Breakdown of(ItemStack stack, Valuation v, @Nullable Offer o, boolean theirs) {
		Breakdown b = new Breakdown();
		b.build(stack, v, o, theirs);
		return b;
	}

	public int height(Font font, int width) {
		int h = 0;
		for (Row r : rows) {
			h += rowHeight(font, r, width);
		}
		return h;
	}

	private static int rowHeight(Font font, Row r, int width) {
		return switch (r) {
			case Title t -> 22 + (t.pills().isEmpty() ? 0 : 12);
			case Section s -> 16;
			case Kv k -> LINE;
			case Note n -> font.split(Component.literal(n.text()), Math.max(40, width - 8 - n.indent() * 8)).size() * 9;
			case BarRow br -> 12;
			case Swatches s -> 14;
			case Gap gap -> 4;
		};
	}

	/** Draws rows top to bottom from {@code y}; only rows inside [clipTop, clipBottom] are drawn. */
	public void render(GuiGraphicsExtractor g, Font font, int x, int y, int w, int clipTop, int clipBottom) {
		int cy = y;
		for (Row r : rows) {
			int rh = rowHeight(font, r, w);
			if (cy + rh >= clipTop && cy <= clipBottom) {
				draw(g, font, r, x, cy, w);
			}
			cy += rh;
		}
	}

	private static void draw(GuiGraphicsExtractor g, Font font, Row r, int x, int y, int w) {
		switch (r) {
			case Title t -> {
				g.fill(x - 1, y, x + 19, y + 20, Draw.BG_CARD);
				g.item(t.stack(), x + 1, y + 2);
				var lines = font.split(t.name(), w - 24);
				if (!lines.isEmpty()) {
					g.text(font, lines.get(0), x + 23, y + 1, Draw.WHITE, true);
				}
				g.text(font, Draw.ellipsize(font, t.sub(), w - 24), x + 23, y + 11, Draw.DARK_GRAY, false);
				int px = x;
				for (String[] p : t.pills()) {
					px += Draw.pill(g, font, px, y + 22, p[0], (int) Long.parseLong(p[1], 16), Draw.WHITE) + 3;
				}
			}
			case Section s -> {
				g.fill(x - 2, y + 3, x + w + 2, y + 15, 0x40000000);
				g.fill(x - 2, y + 3, x, y + 15, s.accent());
				g.text(font, s.title(), x + 3, y + 5, Draw.WHITE, true);
				if (!s.right().isEmpty()) {
					Draw.rightText(g, font, s.right(), x + w, y + 5, Draw.WHITE);
				}
			}
			case Kv k -> {
				int lx = x + k.indent() * 8;
				int vw = font.width(k.value());
				String label = Draw.ellipsize(font, k.label(), w - (lx - x) - vw - 6);
				g.text(font, label, lx, y + 1, k.strong() ? Draw.TEXT : Draw.GRAY, k.strong());
				g.text(font, k.value(), x + w - vw, y + 1, k.valueColor(), true);
			}
			case Note n -> {
				int ny = y;
				for (var line : font.split(Component.literal(n.text()), Math.max(40, w - 8 - n.indent() * 8))) {
					g.text(font, line, x + n.indent() * 8 + 2, ny, Draw.DARK_GRAY, false);
					ny += 9;
				}
			}
			case BarRow br -> {
				Draw.bar(g, x, y + 2, w, 4, br.fraction(), br.color());
				g.text(font, br.left(), x, y + 7 - 3, Draw.DARK_GRAY, false);
			}
			case Swatches s -> {
				int sx = x;
				for (int i = 0; i < s.colors().length; i++) {
					Draw.swatch(g, sx, y + 1, 11, s.colors()[i]);
					g.text(font, s.labels()[i], sx + 14, y + 2, Draw.GRAY, false);
					sx += 14 + font.width(s.labels()[i]) + 10;
				}
			}
			case Gap gap -> {
			}
		}
	}

	// ------------------------------------------------------------------ content

	private void build(ItemStack stack, Valuation v, @Nullable Offer o, boolean theirs) {
		LowballConfig cfg = LowballConfig.get();
		List<String[]> pills = new ArrayList<>();
		if (v.exotic() != null) {
			pills.add(new String[]{v.exotic().type().label.toUpperCase(java.util.Locale.ROOT), "FFB0308C"});
		}
		if (v.item().recombs > 0) {
			pills.add(new String[]{"RECOMB", "FF8A2BE2"});
		}
		if (v.item().stars > 0) {
			pills.add(new String[]{v.item().stars + "★", "FFB8860B"});
		}
		if (v.item().pet) {
			pills.add(new String[]{"LVL " + v.item().petLevel, "FF2E7D32"});
		}
		if (v.item().isClean() && !v.bazaar() && !v.item().pet) {
			pills.add(new String[]{"CLEAN", "FF2F4F6F"});
		}
		rows.add(new Title(stack, stack.getHoverName(), v.item().key + (v.item().count > 1 ? "  ×" + v.item().count : ""), pills));

		// ---- result
		rows.add(new Section("Result", "", Draw.ACCENT));
		rows.add(new Kv("Worth", Fmt.coins(v.totalValue()), Draw.WHITE, 0, true));
		if (o != null && theirs && v.known()) {
			rows.add(new Kv("Suggested offer", Fmt.coins(o.totalOffer()) + "  " + Fmt.percent(o.percent()), Draw.GREEN, 0, true));
			rows.add(new Kv("Resell after tax", Fmt.coins(o.resaleNet()), Draw.TEXT, 0, false));
			rows.add(new Kv("Profit", (o.profit() >= 0 ? "+" : "") + Fmt.coins(o.profit()), o.profit() >= 0 ? Draw.GREEN : Draw.RED, 0, true));
			rows.add(new BarRow(o.percent(), Draw.GREEN, "", ""));
		}
		rows.add(new Note("Worth = base " + Fmt.coins(v.unitBase()) + " + upgrades " + Fmt.coins(v.upgradesCredited())
				+ (v.item().count > 1 ? ", × " + v.item().count : "") + " = " + Fmt.coins(v.totalValue()), 0));

		// ---- base price
		rows.add(new Section("① Base price", Fmt.coins(v.unitBase()), Draw.AQUA));
		for (Valuation.Candidate c : v.candidates()) {
			boolean has = c.value() > 0 && !Double.isNaN(c.value());
			rows.add(new Kv((c.chosen() ? "§a✔ §f" : "  ") + c.label(), has ? Fmt.coins(c.value()) : "—",
					c.chosen() ? Draw.GREEN : has ? Draw.TEXT : Draw.DARK_GRAY, 0, c.chosen()));
			if (!c.detail().isEmpty() && (c.chosen() || has)) {
				rows.add(new Note(c.detail(), 1));
			}
		}
		rows.add(new Note("Using " + v.baseSource() + modeHint(cfg), 0));

		// ---- market
		rows.add(new Section("Market", v.liquidity().label, Draw.liquidityColor(v.liquidity())));
		rows.add(new Kv("Sales / day", Double.isNaN(v.dailyVolume()) ? "?" : Fmt.volume(v.dailyVolume()), Draw.liquidityColor(v.liquidity()), 0, false));
		rows.add(new Note(v.volumeSource(), 1));
		AuctionStats ah = v.auction();
		if (ah != null) {
			rows.add(new Kv("On AH", ah.binCount() + " BIN + " + ah.auctionCount() + " auction", Draw.YELLOW, 0, false));
			StringBuilder sb = new StringBuilder();
			for (double p : ah.cheapest()) {
				sb.append(sb.isEmpty() ? "" : ", ").append(Fmt.coins(p));
			}
			rows.add(new Note("Cheapest: " + sb, 1));
		} else if (v.bazaarProduct() != null) {
			var bz = v.bazaarProduct();
			rows.add(new Kv("Insta-sell / buy", Fmt.coins(bz.instaSell()) + " / " + Fmt.coins(bz.instaBuy()), Draw.TEXT, 0, false));
			rows.add(new Kv("Sell offers / buy orders", bz.sellOffers() + " / " + bz.buyOrders(), Draw.YELLOW, 0, false));
		} else if (v.listed() >= 0) {
			rows.add(new Kv("On AH", v.listed() + "", Draw.YELLOW, 0, false));
		}
		if (!Double.isNaN(v.daysToClear())) {
			rows.add(new Kv("Supply clears in", Fmt.days(v.daysToClear()), Draw.TEXT, 0, false));
		}

		// ---- exotic
		Valuation.ExoticInfo ex = v.exotic();
		if (ex != null) {
			rows.add(new Section("Exotic: " + ex.type().label, Double.isNaN(ex.estimate()) ? "?" : Fmt.coins(ex.estimate()), Draw.PINK));
			rows.add(new Swatches(ex.defaultColor() >= 0 ? new int[]{ex.color(), ex.defaultColor()} : new int[]{ex.color()},
					ex.defaultColor() >= 0 ? new String[]{"#" + ex.hex(), String.format("normal #%06X", ex.defaultColor())} : new String[]{"#" + ex.hex()}));
			history("This color, 30d", ex.exactSales());
			history(ex.type().label + " colors, 30d", ex.typeSales());
			if (ex.exactListings() != null) {
				rows.add(new Kv("Same color on AH", ex.exactListings().binCount() + " from " + Fmt.coins(ex.exactListings().lowest()), Draw.YELLOW, 0, false));
			}
			if (ex.typeListings() != null) {
				rows.add(new Kv(ex.type().label + " on AH", ex.typeListings().binCount() + " from " + Fmt.coins(ex.typeListings().lowest()), Draw.YELLOW, 0, false));
			}
			rows.add(new Note("Estimate: " + ex.estimateSource() + ". Exotics are collector items: ask around before buying big.", 0));
		}

		// ---- upgrades by category
		if (!v.upgrades().isEmpty()) {
			rows.add(new Section("② Upgrades", "+" + Fmt.coins(v.upgradesCredited()), Draw.GOLD));
			rows.add(new Note(Fmt.coins(v.upgradesRaw()) + " to apply today, " + cfg.upgradeMode.label.toLowerCase(java.util.Locale.ROOT) + " credit", 0));
			Map<UpgradeCategory, List<Valuation.Credited>> byCat = new LinkedHashMap<>();
			for (Valuation.Credited c : v.upgrades()) {
				byCat.computeIfAbsent(c.upgrade().category(), k -> new ArrayList<>()).add(c);
			}
			List<Map.Entry<UpgradeCategory, List<Valuation.Credited>>> cats = new ArrayList<>(byCat.entrySet());
			cats.sort((a, b) -> Double.compare(sum(b.getValue()), sum(a.getValue())));
			for (var e : cats) {
				double raw = sum(e.getValue());
				double credit = e.getValue().get(0).credit();
				rows.add(new Kv(e.getKey().label, Fmt.coins(raw) + " §8×" + Math.round(credit * 100) + "% §a+" + Fmt.coins(raw * credit), Draw.TEXT, 0, true));
				List<Valuation.Credited> items = new ArrayList<>(e.getValue());
				items.sort((a, b) -> Double.compare(b.total(), a.total()));
				for (Valuation.Credited c : items) {
					var u = c.upgrade();
					String qty = u.quantity() != 1 ? Fmt.volume(u.quantity()) + "× " : "";
					rows.add(new Kv(qty + u.label(), Fmt.coins(u.total()), Draw.GRAY, 1, false));
				}
			}
		}

		// ---- crafting cost
		CraftCost craft = v.craft();
		if (craft != null) {
			String right = craft.loading() && craft.lines().isEmpty() ? "loading…" : craft.complete() ? Fmt.coins(craft.total()) : "incomplete";
			rows.add(new Section("Crafting cost", right, 0xFF7AA2FF));
			if (!craft.lines().isEmpty()) {
				rows.add(new Note(prettyType(craft.type()) + (craft.loading() ? " (sub-recipes loading…)" : ""), 0));
				for (CraftCost.Line l : craft.lines()) {
					String qty = Fmt.volume(l.quantity()) + "× ";
					String val = Double.isNaN(l.unitPrice()) ? "§c?" : Fmt.coins(l.total());
					rows.add(new Kv(qty + l.name(), val, Draw.TEXT, 0, false));
					if (!Double.isNaN(l.unitPrice())) {
						rows.add(new Note("@ " + Fmt.coins(l.unitPrice()) + " " + l.source(), 1));
					}
				}
				if (craft.coins() > 0) {
					rows.add(new Kv("Coins", Fmt.coins(craft.coins()), Draw.GOLD, 0, false));
				}
				if (craft.complete() && v.auction() != null && !Double.isNaN(v.auction().lowest())) {
					double diff = v.auction().lowest() - craft.total();
					rows.add(new Note(diff >= 0 ? "Crafting is " + Fmt.coins(diff) + " cheaper than the LBIN" : "Buying is " + Fmt.coins(-diff) + " cheaper than crafting", 0));
				}
			}
		}

		// ---- offer math
		if (o != null && theirs && v.known()) {
			rows.add(new Section("③ Offer", Fmt.coins(o.totalOffer()), Draw.GREEN));
			rows.add(new Kv(cfg.preset.label + " preset", Math.round(cfg.basePercent()) + "%", Draw.YELLOW, 0, false));
			for (String a : o.adjustments()) {
				rows.add(new Note(a, 1));
			}
			rows.add(new Kv("Final", Fmt.percent(o.percent()) + " of " + Fmt.coins(v.totalValue()), Draw.GREEN, 0, true));
			if (cfg.roundMode.sig > 0) {
				rows.add(new Note("Rounded down to " + cfg.roundMode.sig + " digits", 1));
			}
			rows.add(new Note("Resale after " + (v.bazaar() ? "bazaar tax " + cfg.bazaarTaxPercent + "%" : "AH listing fee + 1% claim tax")
					+ ": " + Fmt.coins(o.resaleNet()), 0));
		}

		// ---- warnings
		if (!v.warnings().isEmpty()) {
			rows.add(new Section("Heads up", "", Draw.RED));
			for (String w : v.warnings()) {
				rows.add(new Note("§c⚠ §7" + w, 0));
			}
		}
		rows.add(new Gap());
	}

	private void history(String label, CoflnetClient.@Nullable History h) {
		if (h == null) {
			rows.add(new Kv(label, "loading…", Draw.DARK_GRAY, 0, false));
		} else if (!h.any()) {
			rows.add(new Kv(label, "no sales", Draw.DARK_GRAY, 0, false));
		} else {
			rows.add(new Kv(label, h.sales() + " sold, med " + Fmt.coins(h.median()), Draw.TEXT, 0, false));
			rows.add(new Note("range " + Fmt.coins(h.min()) + " – " + Fmt.coins(h.max()) + (h.lastSale() > 0 ? ", last " + Fmt.ago(h.lastSale()) : ""), 1));
		}
	}

	private static double sum(List<Valuation.Credited> l) {
		double t = 0;
		for (Valuation.Credited c : l) {
			t += c.total();
		}
		return t;
	}

	private static String prettyType(String type) {
		return switch (type) {
			case "forge" -> "Forge recipe";
			case "npc_shop" -> "Bought from an NPC";
			default -> "Crafting recipe";
		};
	}

	private static String modeHint(LowballConfig cfg) {
		return switch (cfg.valueMode) {
			case SMART -> "";
			case LBIN -> " (LBIN mode)";
			case MEDIAN -> " (median mode)";
			case LOWEST -> " (lowest of LBIN/median/craft)";
		};
	}
}
