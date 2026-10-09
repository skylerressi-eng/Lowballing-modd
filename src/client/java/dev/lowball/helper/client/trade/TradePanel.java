package dev.lowball.helper.client.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import org.joml.Vector2i;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import dev.lowball.helper.client.feature.SignAutofill;
import dev.lowball.helper.client.mixin.AbstractContainerScreenAccessor;
import dev.lowball.helper.client.ui.Draw;
import dev.lowball.helper.client.ui.QuickSettings;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.Upgrade;
import dev.lowball.helper.valuation.Valuation;

/** The side panel next to the Hypixel trade menu. */
public final class TradePanel {
	private static final int MAX_WIDTH = 200;
	private static final int MIN_WIDTH = 140;
	private static final int PAD = 5;
	private static final int LINE = 10;
	private static final int GAP = 4;
	private static final Map<ContainerScreen, TradePanel> PANELS = new WeakHashMap<>();
	private static boolean collapsed;

	private final ContainerScreen screen;
	private final String partner;
	private final QuickSettings settings = new QuickSettings();
	private final List<Hotspot> hotspots = new ArrayList<>();

	private @Nullable TradeState state;
	private long stateSignature;
	private int stateMarketVersion = -1;
	private int stateConfigRevision = -1;
	private long stateBuiltAt;

	private boolean showSettings;
	private int scroll;
	private int maxScroll;
	/** Panel geometry in panel-local (scaled) coordinates. */
	private int x, y, w, h;
	private int listTop, listBottom;
	private float scale = 1;
	private int screenW, screenH;
	private @Nullable TradeEntry hoveredEntry;
	private @Nullable List<Component> pendingTooltip;

	private record Hotspot(@Nullable String id, int x0, int y0, int x1, int y1, Click action) {
	}

	@FunctionalInterface
	private interface Click {
		void run(int button, boolean shift);
	}

	private TradePanel(ContainerScreen screen, String partner) {
		this.screen = screen;
		this.partner = partner;
	}

	/** Called for every screen init; attaches to trade menus only. */
	public static void attach(ContainerScreen screen, String partner) {
		TradePanel panel = PANELS.computeIfAbsent(screen, s -> new TradePanel(s, partner));
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> panel.render(g, mx, my));
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !panel.click(event.x(), event.y(), event.button(), event.hasShiftDown()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !panel.scrolled(mx, my, v));
	}

	// ------------------------------------------------------------------ state

	private void refreshState() {
		long sig = 17;
		for (Slot slot : screen.getMenu().slots) {
			if (slot.index >= 36) {
				break;
			}
			ItemStack st = slot.getItem();
			sig = sig * 31 + (st.isEmpty() ? 0 : System.identityHashCode(st) * 7L + st.getCount());
		}
		int mv = Market.get().version();
		int cr = LowballConfig.revision();
		long now = System.currentTimeMillis();
		boolean slotsChanged = sig != stateSignature;
		boolean dataChanged = mv != stateMarketVersion || cr != stateConfigRevision;
		if (state == null || slotsChanged || (dataChanged && now - stateBuiltAt > 250)) {
			state = TradeState.build(screen.getMenu());
			stateSignature = sig;
			stateMarketVersion = mv;
			stateConfigRevision = cr;
			stateBuiltAt = now;
		}
	}

	// ------------------------------------------------------------------ layout

	/** Picks side and scale, in GUI coordinates; false when there is no room at all. */
	private boolean computeScale() {
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		int left = acc.lowball$leftPos();
		int right = left + acc.lowball$imageWidth();
		int rightSpace = screen.width - right - GAP * 2;
		int leftSpace = left - GAP * 2;
		boolean useRight = switch (LowballConfig.get().panelSide) {
			case LEFT -> !(leftSpace >= MIN_WIDTH || leftSpace >= rightSpace);
			default -> rightSpace >= MIN_WIDTH || rightSpace >= leftSpace;
		};
		int space = useRight ? rightSpace : leftSpace;
		if (space < 60) {
			return false;
		}
		double wanted = LowballConfig.get().panelScale > 0 ? LowballConfig.get().panelScale : 1.0;
		scale = (float) Math.max(0.5, Math.min(wanted, space / (double) MAX_WIDTH));
		screenW = (int) (screen.width / scale);
		screenH = (int) (screen.height / scale);
		int spaceL = (int) (space / scale);
		w = Math.min(MAX_WIDTH, spaceL);
		x = useRight ? (int) ((right + GAP) / scale) : (int) ((left - GAP) / scale) - w;
		return true;
	}

	private void layout(int listContentHeight, int footerHeight) {
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		int top = (int) (acc.lowball$topPos() / scale);
		int headerHeight = PAD + LINE * 2 + 3;
		int wanted = headerHeight + listContentHeight + footerHeight + PAD;
		h = Math.min(wanted, screenH - GAP * 2);
		y = Math.max(GAP, Math.min(top, screenH - GAP - h));
		listTop = y + headerHeight;
		listBottom = y + h - footerHeight;
		maxScroll = Math.max(0, listContentHeight - (listBottom - listTop));
		scroll = Math.min(scroll, maxScroll);
	}

	// ------------------------------------------------------------------ rendering

	private void render(GuiGraphicsExtractor g, int guiMx, int guiMy) {
		LowballConfig cfg = LowballConfig.get();
		if (!cfg.panelEnabled) {
			return;
		}
		hotspots.clear();
		hoveredEntry = null;
		pendingTooltip = null;
		Font font = Minecraft.getInstance().font;
		if (!computeScale()) {
			return;
		}
		int mx = (int) Math.floor(guiMx / scale);
		int my = (int) Math.floor(guiMy / scale);
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		try {
			if (collapsed) {
				renderCollapsed(g, font, mx, my);
			} else {
				renderPanel(g, font, cfg, mx, my);
			}
		} finally {
			g.pose().popMatrix();
		}
		if (!collapsed && state != null) {
			highlightSlots(g);
		}
		if (pendingTooltip != null) {
			drawTooltip(g, font, pendingTooltip, guiMx, guiMy);
		}
	}

	/** Draws a tooltip now (the deferred tooltip pass has already run), shrunk to fit on short screens. */
	private void drawTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int guiMx, int guiMy) {
		List<ClientTooltipComponent> comps = lines.stream().map(Component::getVisualOrderText).map(ClientTooltipComponent::create).toList();
		int height = 8;
		for (ClientTooltipComponent c : comps) {
			height += c.getHeight(font);
		}
		float ts = Math.min(1f, (screen.height - 8) / (float) height);
		ClientTooltipPositioner positioner = (sw, sh, mx, my, tw, th) -> {
			int maxW = (int) (screen.width / ts);
			int maxH = (int) (screen.height / ts);
			int tx = mx + 12;
			if (tx + tw + 4 > maxW) {
				tx = Math.max(4, mx - 16 - tw);
			}
			int ty = Math.max(4, Math.min(my - 12, maxH - th - 4));
			return new Vector2i(tx, ty);
		};
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().scale(ts, ts);
		g.tooltip(font, comps, (int) (guiMx / ts), (int) (guiMy / ts), positioner, null);
		g.pose().popMatrix();
	}

	private void renderPanel(GuiGraphicsExtractor g, Font font, LowballConfig cfg, int mx, int my) {
		refreshState();
		TradeState s = state;
		boolean compact = cfg.compactRows;
		int rowH = compact ? 21 : 32;
		int listHeight;
		if (showSettings) {
			listHeight = settings.height();
		} else {
			int rows = s.theirs.size() + (cfg.showYourSide ? s.yours.size() : 0);
			int headers = 1 + (cfg.showYourSide && !s.yours.isEmpty() ? 1 : 0);
			listHeight = rows * rowH + headers * (LINE + 2) + (s.theirs.isEmpty() ? LINE * 2 : 0);
		}
		layout(listHeight, footerHeight(s));

		Draw.panel(g, x, y, w, h);
		g.fill(x + 1, y + 1, x + w - 1, y + PAD + LINE * 2 + 1, Draw.BG_HEADER);
		renderHeader(g, font, mx, my);

		g.enableScissor(x + 1, listTop, x + w - 1, listBottom);
		if (showSettings) {
			String hint = settings.render(g, font, x + PAD, listTop - scroll, w - PAD * 2, mx, my, (x0, y0, x1, y1, click) -> {
				if (y1 > listTop && y0 < listBottom) {
					hotspots.add(new Hotspot(null, x0, Math.max(y0, listTop), x1, Math.min(y1, listBottom), click::accept));
				}
			});
			if (hint != null && Draw.inside(mx, my, x, listTop, x + w, listBottom)) {
				pendingTooltip = new ArrayList<>();
				for (FormattedCharSequence line : font.split(Component.literal(hint), 180)) {
					pendingTooltip.add(Component.literal(toPlain(line)).withStyle(net.minecraft.ChatFormatting.GRAY));
				}
			}
		} else {
			renderList(g, font, s, rowH, compact, mx, my);
		}
		g.disableScissor();
		if (maxScroll > 0) {
			int track = listBottom - listTop;
			int thumb = Math.max(10, track * track / (track + maxScroll));
			int ty = listTop + (track - thumb) * scroll / maxScroll;
			g.fill(x + w - 3, ty, x + w - 1, ty + thumb, 0x80FFFFFF);
		}

		renderFooter(g, font, s, mx, my);
		if (hoveredEntry != null && Draw.inside(mx, my, x, listTop, x + w, listBottom)) {
			pendingTooltip = tooltip(hoveredEntry);
		}
	}

	private static String toPlain(FormattedCharSequence seq) {
		StringBuilder sb = new StringBuilder();
		seq.accept((index, style, codePoint) -> {
			sb.appendCodePoint(codePoint);
			return true;
		});
		return sb.toString();
	}

	private void renderCollapsed(GuiGraphicsExtractor g, Font font, int mx, int my) {
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		String label = "◀ Lowball";
		int bw = font.width(label) + 10;
		int by = (int) (acc.lowball$topPos() / scale);
		int bx = (int) ((acc.lowball$leftPos() + acc.lowball$imageWidth() + GAP) / scale);
		if (bx + bw > screenW) {
			label = "Lowball ▶";
			bx = (int) ((acc.lowball$leftPos() - GAP) / scale) - bw;
		}
		boolean hover = Draw.inside(mx, my, bx, by, bx + bw, by + 14);
		Draw.button(g, font, bx, by, bw, 14, label, hover, Draw.GOLD);
		hotspots.add(new Hotspot("expand", bx, by, bx + bw, by + 14, (b, sh) -> collapsed = false));
	}

	private void renderHeader(GuiGraphicsExtractor g, Font font, int mx, int my) {
		LowballConfig cfg = LowballConfig.get();
		int ty = y + PAD;
		g.text(font, "§6§lLowball", x + PAD, ty, Draw.WHITE, true);
		int titleW = font.width("§6§lLowball") + 4;
		String vs = Draw.ellipsize(font, "vs " + partner, w - PAD * 2 - titleW - 30);
		g.text(font, vs, x + PAD + titleW, ty, Draw.GRAY, true);

		// collapse + settings icons
		int ix = x + w - PAD - 9;
		iconButton(g, font, ix, ty - 1, "–", mx, my, "collapse", (b, sh) -> collapsed = true);
		iconButton(g, font, ix - 12, ty - 1, showSettings ? "§e⚙" : "⚙", mx, my, "settings", (b, sh) -> {
			showSettings = !showSettings;
			scroll = 0;
		});

		// preset line
		int py = ty + LINE + 1;
		String preset = cfg.preset.label + " §f" + Math.round(cfg.basePercent()) + "%";
		int bx = x + PAD;
		iconButton(g, font, bx, py - 1, "◀", mx, my, null, (b, sh) -> {
			cfg.preset = cfg.preset.previous();
			cfg.save();
		});
		int labelX = bx + 11;
		int labelW = w - PAD * 2 - 11 - 40;
		boolean hover = Draw.inside(mx, my, labelX, py - 1, labelX + labelW, py + 9);
		g.centeredText(font, "§e" + preset, labelX + labelW / 2, py, hover ? Draw.WHITE : Draw.YELLOW);
		hotspots.add(new Hotspot("preset", labelX, py - 1, labelX + labelW, py + 9, (b, sh) -> {
			cfg.preset = b == 1 ? cfg.preset.previous() : cfg.preset.next();
			cfg.save();
		}));
		iconButton(g, font, labelX + labelW, py - 1, "▶", mx, my, null, (b, sh) -> {
			cfg.preset = cfg.preset.next();
			cfg.save();
		});
		int rx = x + w - PAD - 9;
		iconButton(g, font, rx, py - 1, "+", mx, my, null, (b, sh) -> {
			cfg.setBasePercent(cfg.basePercent() + (sh ? 5 : 1));
			cfg.save();
		});
		iconButton(g, font, rx - 12, py - 1, "-", mx, my, null, (b, sh) -> {
			cfg.setBasePercent(cfg.basePercent() - (sh ? 5 : 1));
			cfg.save();
		});
		g.horizontalLine(x + 1, x + w - 2, y + PAD + LINE * 2 + 1, Draw.BORDER);
	}

	private void iconButton(GuiGraphicsExtractor g, Font font, int bx, int by, String label, int mx, int my, @Nullable String id, Click click) {
		boolean hover = Draw.inside(mx, my, bx, by, bx + 10, by + 10);
		if (hover) {
			g.fill(bx, by, bx + 10, by + 10, Draw.HOVER);
		}
		g.centeredText(font, label, bx + 5, by + 1, hover ? Draw.GOLD : Draw.GRAY);
		hotspots.add(new Hotspot(id, bx, by, bx + 10, by + 10, click));
	}

	private void renderList(GuiGraphicsExtractor g, Font font, TradeState s, int rowH, boolean compact, int mx, int my) {
		LowballConfig cfg = LowballConfig.get();
		int cy = listTop - scroll;
		g.text(font, "§7They give §8(" + s.theirs.size() + ")", x + PAD, cy + 2, Draw.WHITE, true);
		cy += LINE + 2;
		if (s.theirs.isEmpty()) {
			g.text(font, "§8Waiting for their items…", x + PAD, cy + 4, Draw.WHITE, true);
			cy += LINE * 2;
		}
		for (TradeEntry e : s.theirs) {
			renderEntry(g, font, e, cy, rowH, compact, mx, my, true);
			cy += rowH;
		}
		if (cfg.showYourSide && !s.yours.isEmpty()) {
			g.text(font, "§7You give §8(" + s.yours.size() + ")", x + PAD, cy + 2, Draw.WHITE, true);
			cy += LINE + 2;
			for (TradeEntry e : s.yours) {
				renderEntry(g, font, e, cy, rowH, compact, mx, my, false);
				cy += rowH;
			}
		}
	}

	private void renderEntry(GuiGraphicsExtractor g, Font font, TradeEntry e, int ey, int rowH, boolean compact, int mx, int my, boolean theirs) {
		if (ey + rowH < listTop || ey > listBottom) {
			return;
		}
		boolean hover = Draw.inside(mx, my, x + 1, ey, x + w - 1, ey + rowH - 1) && Draw.inside(mx, my, x, listTop, x + w, listBottom);
		Slot hoveredSlot = ((AbstractContainerScreenAccessor) screen).lowball$hoveredSlot();
		if (hover || (hoveredSlot != null && hoveredSlot == e.slot())) {
			g.fill(x + 1, ey, x + w - 1, ey + rowH - 1, Draw.HOVER);
		}
		if (hover) {
			hoveredEntry = e;
		}
		int iconY = ey + (rowH - 1 - 16) / 2;
		g.item(e.stack(), x + PAD, iconY);
		g.itemDecorations(font, e.stack(), x + PAD, iconY);
		int tx = x + PAD + 20;
		int tw = w - PAD * 2 - 20 - 2;
		int l1 = ey + 2;
		int l2 = l1 + LINE;
		int l3 = l2 + LINE;

		Valuation v = e.valuation();
		Offer o = e.offer();
		String warn = v != null && !v.warnings().isEmpty() ? " §c⚠" : "";
		int warnW = warn.isEmpty() ? 0 : font.width(warn);

		if (e.isCoins()) {
			g.text(font, "§6" + Fmt.coins(e.coins()) + " coins", tx, l1, Draw.WHITE, true);
			g.text(font, "§8" + Fmt.full(e.coins()), tx, l2, Draw.WHITE, true);
			return;
		}
		String right = compact && v != null && v.known() ? (theirs ? "§a" + Fmt.coins(o.totalOffer()) : "§f" + Fmt.coins(v.totalValue())) : "";
		int rightW = font.width(right);
		drawName(g, font, e.stack(), tx, l1, tw - warnW - (rightW > 0 ? rightW + 4 : 0));
		if (!warn.isEmpty()) {
			g.text(font, warn, tx + tw - warnW - (rightW > 0 ? rightW + 4 : 0), l1, Draw.WHITE, true);
		}
		if (rightW > 0) {
			Draw.rightText(g, font, right, tx + tw, l1, Draw.WHITE);
		}

		if (v == null) {
			g.text(font, "§8Not a SkyBlock item", tx, l2, Draw.WHITE, true);
			return;
		}
		if (!v.known()) {
			g.text(font, "§cNo price data §8(" + Draw.ellipsize(font, v.item().key, tw - 70) + ")", tx, l2, Draw.WHITE, true);
			return;
		}
		g.text(font, statsLine(v), tx, l2, Draw.WHITE, true);
		if (!compact) {
			String line3 = theirs
					? "§7Worth §f" + Fmt.coins(v.totalValue()) + " §8→ §a" + Fmt.coins(o.totalOffer()) + " §7(" + Fmt.percent(o.percent()) + ")"
					: "§7Worth §f" + Fmt.coins(v.totalValue());
			g.text(font, line3, tx, l3, Draw.WHITE, true);
		}
	}

	private static void drawName(GuiGraphicsExtractor g, Font font, ItemStack stack, int tx, int ty, int maxW) {
		Component name = stack.getHoverName();
		List<FormattedCharSequence> lines = font.split(name, Math.max(20, maxW));
		if (!lines.isEmpty()) {
			g.text(font, lines.get(0), tx, ty, Draw.WHITE, true);
		}
	}

	private static String statsLine(Valuation v) {
		String vol = Double.isNaN(v.dailyVolume()) ? "§8?/d" : colorOf(v) + Fmt.volume(v.dailyVolume()) + "/d";
		if (v.bazaar()) {
			return "§7BZ §f" + Fmt.coins(v.unitBase()) + " §8· " + vol;
		}
		String listed = v.listed() < 0 ? "§8? AH" : "§e" + v.listed() + " §7AH";
		AuctionStats ah = v.auction();
		String price = ah != null && !Double.isNaN(ah.lowest()) ? "§7LBIN §f" + Fmt.coins(ah.lowest()) : "§7" + shortSource(v.baseSource()) + " §f" + Fmt.coins(v.unitBase());
		return price + " §8· " + vol + " §8· " + listed;
	}

	private static String shortSource(String src) {
		return src.startsWith("Sold median") ? "Med" : src.startsWith("NPC") ? "NPC" : "Val";
	}

	private static String colorOf(Valuation v) {
		return switch (v.liquidity()) {
			case FAST -> "§a";
			case OK -> "§e";
			case SLOW -> "§6";
			case ILLIQUID -> "§c";
			default -> "§7";
		};
	}

	// ------------------------------------------------------------------ footer

	private int footerHeight(TradeState s) {
		int lines = 3; // value, offer, profit
		if (s.yourCoins > 0 || s.yourItemsValue > 0) {
			lines += 2;
		}
		if (s.unknownItems > 0) {
			lines++;
		}
		return 4 + lines * LINE + 4 + 14 + 4 + LINE;
	}

	private void renderFooter(GuiGraphicsExtractor g, Font font, TradeState s, int mx, int my) {
		int fy = listBottom;
		g.horizontalLine(x + 1, x + w - 2, fy, Draw.BORDER);
		g.fill(x + 1, fy + 1, x + w - 1, y + h - 1, Draw.BG_HEADER);
		int ly = fy + 4;
		int lx = x + PAD;
		int rx = x + w - PAD;

		g.text(font, "§7Their items worth", lx, ly, Draw.WHITE, true);
		Draw.rightText(g, font, "§f" + Fmt.coins(s.theirItemsValue), rx, ly, Draw.WHITE);
		ly += LINE;
		g.text(font, "§7Suggested offer", lx, ly, Draw.WHITE, true);
		Draw.rightText(g, font, "§a§l" + Fmt.coins(s.suggestedOffer) + " §r§7" + Fmt.percent(s.suggestedPercent()), rx, ly, Draw.WHITE);
		ly += LINE;
		double profit = s.resaleNet - s.suggestedOffer;
		g.text(font, "§7Profit after tax", lx, ly, Draw.WHITE, true);
		Draw.rightText(g, font, (profit >= 0 ? "§a+" : "§c") + Fmt.coins(profit), rx, ly, Draw.WHITE);
		ly += LINE;

		if (s.yourCoins > 0 || s.yourItemsValue > 0) {
			double paying = s.yourCoins + s.yourItemsValue;
			String color = paying <= s.suggestedOffer * 1.001 ? "§a" : paying <= s.theirItemsValue * 0.95 ? "§e" : "§c";
			g.text(font, "§7You're paying", lx, ly, Draw.WHITE, true);
			Draw.rightText(g, font, color + Fmt.coins(paying) + " §7" + Fmt.percent(s.currentPercent()), rx, ly, Draw.WHITE);
			ly += LINE;
			double now = s.currentProfit();
			g.text(font, "§7Profit at that price", lx, ly, Draw.WHITE, true);
			Draw.rightText(g, font, (now >= 0 ? "§a+" : "§c") + Fmt.coins(now), rx, ly, Draw.WHITE);
			ly += LINE;
		}
		if (s.unknownItems > 0) {
			g.text(font, "§c⚠ " + s.unknownItems + " item(s) without price", lx, ly, Draw.WHITE, true);
			ly += LINE;
		}

		ly += 4;
		int bw = (w - PAD * 2 - 4) / 2;
		boolean can = s.suggestedOffer > 0;
		boolean h1 = can && Draw.inside(mx, my, lx, ly, lx + bw, ly + 14);
		Draw.button(g, font, lx, ly, bw, 14, "Copy offer", h1, can ? Draw.WHITE : Draw.DARK_GRAY);
		boolean h2 = can && Draw.inside(mx, my, lx + bw + 4, ly, lx + bw * 2 + 4, ly + 14);
		Draw.button(g, font, lx + bw + 4, ly, bw, 14, SignAutofill.armed() ? "§aArmed ✔" : "Autofill coins", h2, can ? Draw.GOLD : Draw.DARK_GRAY);
		if (can) {
			hotspots.add(new Hotspot("copy", lx, ly, lx + bw, ly + 14, (b, sh) -> SignAutofill.copy(s.suggestedOffer)));
			hotspots.add(new Hotspot("autofill", lx + bw + 4, ly, lx + bw * 2 + 4, ly + 14, (b, sh) -> SignAutofill.arm(s.suggestedOffer)));
		}
		ly += 14 + 3;
		g.text(font, Draw.ellipsize(font, statusLine(), w - PAD * 2), lx, ly, Draw.DARK_GRAY, false);
	}

	private static String statusLine() {
		Market m = Market.get();
		if (m.lastError() != null && !m.hasAnyData()) {
			return "Offline: " + m.lastError();
		}
		if (m.scanning() && m.auctionsFetchedAt() == 0) {
			return "Scanning auction house…";
		}
		String ah = m.auctionsFetchedAt() > 0 ? "AH " + Fmt.ago(m.auctionsFetchedAt()) : (LowballConfig.get().ahScanMinutes > 0 ? "AH loading…" : "AH scan off");
		String bz = m.bazaarFetchedAt() > 0 ? "BZ " + Fmt.ago(m.bazaarFetchedAt()) : "BZ loading…";
		return ah + (m.scanning() ? " (updating)" : "") + " · " + bz;
	}

	private void highlightSlots(GuiGraphicsExtractor g) {
		if (hoveredEntry == null) {
			return;
		}
		AbstractContainerScreenAccessor acc = (AbstractContainerScreenAccessor) screen;
		Slot slot = hoveredEntry.slot();
		int sx = acc.lowball$leftPos() + slot.x;
		int sy = acc.lowball$topPos() + slot.y;
		g.fill(sx, sy, sx + 16, sy + 16, 0x6055FF55);
		g.outline(sx - 1, sy - 1, 18, 18, Draw.GREEN);
	}

	// ------------------------------------------------------------------ tooltip

	private static List<Component> tooltip(TradeEntry e) {
		List<Component> lines = new ArrayList<>();
		lines.add(e.stack().getHoverName());
		Valuation v = e.valuation();
		if (e.isCoins()) {
			lines.add(Component.literal("§6" + Fmt.full(e.coins()) + " coins"));
			return lines;
		}
		if (v == null) {
			lines.add(Component.literal("§8No SkyBlock id on this item"));
			return lines;
		}
		LowballConfig cfg = LowballConfig.get();
		lines.add(Component.literal("§8" + v.item().key + (v.item().count > 1 ? " ×" + v.item().count : "")));
		lines.add(Component.empty());
		lines.add(Component.literal("§7Base: §f" + Fmt.coins(v.unitBase()) + " §8(" + v.baseSource() + ")"));
		AuctionStats ah = v.auction();
		if (ah != null) {
			StringBuilder sb = new StringBuilder("§7Cheapest: ");
			for (int i = 0; i < ah.cheapest().length; i++) {
				sb.append(i == 0 ? "§f" : "§8, §7").append(Fmt.coins(ah.cheapest()[i]));
			}
			lines.add(Component.literal(sb.toString()));
			lines.add(Component.literal("§7Listed: §e" + ah.binCount() + " BIN §8+ §e" + ah.auctionCount() + " auctions"
					+ (!Double.isNaN(ah.cleanLowest()) && ah.cleanLowest() != ah.lowest() ? " §8· §7clean §f" + Fmt.coins(ah.cleanLowest()) : "")));
		} else if (v.bazaarProduct() != null) {
			var bz = v.bazaarProduct();
			lines.add(Component.literal("§7Insta-sell §f" + Fmt.coins(bz.instaSell()) + " §8· §7Insta-buy §f" + Fmt.coins(bz.instaBuy())));
			lines.add(Component.literal("§7Sell offers: §e" + bz.sellOffers() + " §8· §7Buy orders: §e" + bz.buyOrders()));
		}
		if (!Double.isNaN(v.median())) {
			lines.add(Component.literal("§7Sold median (24h): §f" + Fmt.coins(v.median())));
		}
		String vol = Double.isNaN(v.dailyVolume()) ? "§8unknown" : "§f" + Fmt.volume(v.dailyVolume()) + "/day §8(" + v.volumeSource() + ")";
		lines.add(Component.literal("§7Volume: " + vol));
		lines.add(Component.literal("§7Liquidity: " + colorOf(v) + v.liquidity().label
				+ (Double.isNaN(v.daysToClear()) ? "" : " §8· §7supply clears in §f" + Fmt.days(v.daysToClear()))));

		if (!v.upgrades().isEmpty()) {
			lines.add(Component.empty());
			lines.add(Component.literal("§7Upgrades §8(" + Fmt.coins(v.upgradesRaw()) + ", credited " + Math.round(v.upgradeCredit() * 100) + "%)"));
			List<Upgrade> ups = new ArrayList<>(v.upgrades());
			ups.sort((a, b) -> Double.compare(b.total(), a.total()));
			int shown = 0;
			for (Upgrade u : ups) {
				if (shown++ >= 8) {
					lines.add(Component.literal("§8 …and " + (ups.size() - 8) + " more"));
					break;
				}
				String qty = u.quantity() != 1 ? " §8×" + Fmt.volume(u.quantity()) : "";
				lines.add(Component.literal(" §8• §7" + u.label() + qty + " §f" + Fmt.coins(u.total())));
			}
		}

		Offer o = e.offer();
		lines.add(Component.empty());
		lines.add(Component.literal("§7Value: §f" + Fmt.coins(v.unitValue()) + (v.item().count > 1 ? " §8× " + v.item().count + " = §f" + Fmt.coins(v.totalValue()) : "")));
		if (v.known() && TradeMenu.isTheirs(e.slot().index)) {
			lines.add(Component.literal("§7Offer: §a" + Fmt.coins(o.totalOffer()) + " §7(" + Fmt.percent(o.percent()) + ", " + cfg.preset.label + " " + Math.round(cfg.basePercent()) + "%)"));
			for (String a : o.adjustments()) {
				lines.add(Component.literal(" §8• " + a));
			}
			lines.add(Component.literal("§7Resell after tax: §f" + Fmt.coins(o.resaleNet()) + " §8→ §7profit " + (o.profit() >= 0 ? "§a+" : "§c") + Fmt.coins(o.profit())));
		}
		for (String warning : v.warnings()) {
			lines.add(Component.literal("§c⚠ " + warning));
		}
		return lines;
	}

	// ------------------------------------------------------------------ input

	/** Center of a named control in GUI coordinates (for automated client tests), or null. */
	public static int @Nullable [] controlCenter(ContainerScreen screen, String id) {
		TradePanel panel = PANELS.get(screen);
		if (panel == null) {
			return null;
		}
		for (Hotspot hs : panel.hotspots) {
			if (id.equals(hs.id)) {
				return new int[]{(int) ((hs.x0 + hs.x1) / 2.0 * panel.scale), (int) ((hs.y0 + hs.y1) / 2.0 * panel.scale)};
			}
		}
		return null;
	}

	/** GUI-space point inside the first item row (for automated client tests). */
	public static int @Nullable [] firstRowCenter(ContainerScreen screen) {
		TradePanel panel = PANELS.get(screen);
		if (panel == null) {
			return null;
		}
		int ry = panel.listTop + LINE + 2 + 10;
		return new int[]{(int) ((panel.x + panel.w / 2.0) * panel.scale), (int) (ry * panel.scale)};
	}

	private boolean click(double guiMx, double guiMy, int button, boolean shift) {
		if (!LowballConfig.get().panelEnabled) {
			return false;
		}
		double mx = guiMx / scale;
		double my = guiMy / scale;
		for (Hotspot hs : List.copyOf(hotspots)) {
			if (Draw.inside(mx, my, hs.x0, hs.y0, hs.x1, hs.y1)) {
				hs.action.run(button, shift);
				AbstractWidget.playButtonClickSound(Minecraft.getInstance().getSoundManager());
				return true;
			}
		}
		// swallow clicks on the panel background so they don't drop items
		return !collapsed && Draw.inside(mx, my, x, y, x + w, y + h);
	}

	private boolean scrolled(double guiMx, double guiMy, double amount) {
		double mx = guiMx / scale;
		double my = guiMy / scale;
		if (collapsed || !Draw.inside(mx, my, x, y, x + w, y + h)) {
			return false;
		}
		scroll = (int) Math.max(0, Math.min(maxScroll, scroll - amount * 14));
		return true;
	}
}
