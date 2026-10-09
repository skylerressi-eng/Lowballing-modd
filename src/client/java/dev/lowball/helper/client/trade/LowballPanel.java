package dev.lowball.helper.client.trade;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;

import dev.lowball.helper.client.Items;
import dev.lowball.helper.client.feature.SignAutofill;
import dev.lowball.helper.client.ScreenAccess;
import dev.lowball.helper.client.ui.Breakdown;
import dev.lowball.helper.client.ui.Draw;
import dev.lowball.helper.client.ui.QuickSettings;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.Market;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.valuation.Offer;
import dev.lowball.helper.valuation.OfferCalculator;
import dev.lowball.helper.valuation.Valuation;
import dev.lowball.helper.valuation.Valuator;

/**
 * The side panel. In a trade menu it lists both sides with totals and a suggested offer; any row (or any item you
 * press the inspect key on, in any menu) opens a full breakdown of how its value was calculated.
 */
public final class LowballPanel {
	private static final int MAX_WIDTH = 210;
	private static final int MIN_WIDTH = 140;
	private static final int PAD = 5;
	private static final int LINE = 10;
	private static final int GAP = 4;
	private static final Map<AbstractContainerScreen<?>, LowballPanel> PANELS = new WeakHashMap<>();
	private static boolean collapsed;
	private static @Nullable KeyMapping inspectKey;

	private enum Mode { LIST, SETTINGS, DETAIL }

	private final AbstractContainerScreen<?> screen;
	private final @Nullable String partner;
	private final QuickSettings settings = new QuickSettings();
	private final List<Hotspot> hotspots = new ArrayList<>();

	private @Nullable TradeState state;
	private long stateSignature;
	private int stateMarketVersion = -1;
	private int stateConfigRevision = -1;
	private long stateBuiltAt;

	private Mode mode = Mode.LIST;
	/** Item shown in DETAIL mode, and whether it's on their side of a trade (so an offer applies). */
	private @Nullable ItemStack detailStack;
	private boolean detailOffer;
	private @Nullable Breakdown breakdown;
	private @Nullable Valuation detailValuation;
	private @Nullable Offer detailOfferResult;
	private int detailVersion = -1;
	private int detailRevision = -1;
	private long detailBuiltAt;

	private int scroll;
	private int maxScroll;
	/** Panel geometry in panel-local (scaled) coordinates. */
	private int x, y, w, h;
	private int bodyTop, bodyBottom;
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

	private LowballPanel(AbstractContainerScreen<?> screen, @Nullable String partner) {
		this.screen = screen;
		this.partner = partner;
	}

	public static void setInspectKey(KeyMapping key) {
		inspectKey = key;
		LowballPanelKey.set(key);
	}

	/** Called on every container screen init; {@code partner} is set for Hypixel trade menus. */
	public static void attach(AbstractContainerScreen<?> screen, @Nullable String partner) {
		LowballPanel panel = PANELS.computeIfAbsent(screen, s -> new LowballPanel(s, partner));
		ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> panel.render(g, mx, my));
		ScreenMouseEvents.allowMouseClick(screen).register((s, event) -> !panel.click(event.x(), event.y(), event.button(), event.hasShiftDown()));
		ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h, v) -> !panel.scrolled(mx, my, v));
		ScreenKeyboardEvents.allowKeyPress(screen).register((s, event) -> {
			if (inspectKey != null && inspectKey.matches(event) && !(s.getFocused() instanceof net.minecraft.client.gui.components.EditBox)) {
				return !panel.inspectHovered();
			}
			if (event.isEscape() && panel.mode == Mode.DETAIL && panel.isTrade()) {
				panel.back();
				return false;
			}
			return true;
		});
	}

	private boolean isTrade() {
		return partner != null;
	}

	/** Panel is drawn for trades always, and for other menus only while inspecting something. */
	private boolean visible() {
		return LowballConfig.get().panelEnabled && (isTrade() || (mode == Mode.DETAIL && detailStack != null));
	}

	// ------------------------------------------------------------------ navigation

	private boolean inspectHovered() {
		Slot slot = ScreenAccess.hoveredSlot(screen);
		if (slot == null || !slot.hasItem() || Items.of(slot.getItem()) == null) {
			return false;
		}
		openDetail(slot.getItem(), isTrade() && TradeMenu.isTheirs(slot.index) || !isTrade());
		collapsed = false;
		return true;
	}

	private void openDetail(ItemStack stack, boolean withOffer) {
		detailStack = stack;
		detailOffer = withOffer;
		breakdown = null;
		mode = Mode.DETAIL;
		scroll = 0;
	}

	private void back() {
		mode = Mode.LIST;
		detailStack = null;
		breakdown = null;
		scroll = 0;
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

	private void refreshDetail() {
		if (detailStack == null) {
			return;
		}
		int mv = Market.get().version();
		int cr = LowballConfig.revision();
		long now = System.currentTimeMillis();
		if (breakdown == null || ((mv != detailVersion || cr != detailRevision) && now - detailBuiltAt > 250)) {
			SkyblockItem item = Items.of(detailStack);
			if (item == null) {
				back();
				return;
			}
			detailValuation = Valuator.value(item, true);
			detailOfferResult = OfferCalculator.offer(detailValuation, LowballConfig.get());
			breakdown = Breakdown.of(detailStack, detailValuation, detailOfferResult, detailOffer);
			detailVersion = mv;
			detailRevision = cr;
			detailBuiltAt = now;
		}
	}

	// ------------------------------------------------------------------ layout

	/** Picks side and scale, in GUI coordinates; false when there is no room at all. */
	private boolean computeScale() {
		int left = ScreenAccess.left(screen);
		int right = left + ScreenAccess.imageWidth(screen);
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
		w = Math.min(MAX_WIDTH, (int) (space / scale));
		x = useRight ? (int) Math.ceil((right + GAP) / scale) : (int) ((left - GAP) / scale) - w;
		return true;
	}

	private int headerHeight() {
		return PAD + LINE * 2 + 4;
	}

	private void layout(int bodyContentHeight, int footerHeight) {
		int top = (int) (ScreenAccess.top(screen) / scale);
		int wanted = headerHeight() + bodyContentHeight + footerHeight + 2;
		// detail views are long: use the full height so less scrolling is needed
		int minH = mode == Mode.DETAIL ? Math.min(screenH - GAP * 2, 260) : 0;
		h = Math.max(minH, Math.min(wanted, screenH - GAP * 2));
		y = Math.max(GAP, Math.min(top, screenH - GAP - h));
		bodyTop = y + headerHeight();
		bodyBottom = y + h - footerHeight;
		maxScroll = Math.max(0, bodyContentHeight - (bodyBottom - bodyTop));
		scroll = Math.max(0, Math.min(scroll, maxScroll));
	}

	// ------------------------------------------------------------------ rendering

	private void render(GuiGraphicsExtractor g, int guiMx, int guiMy) {
		hotspots.clear();
		hoveredEntry = null;
		pendingTooltip = null;
		if (!visible() || !computeScale()) {
			return;
		}
		Font font = Minecraft.getInstance().font;
		int mx = (int) Math.floor(guiMx / scale);
		int my = (int) Math.floor(guiMy / scale);
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().scale(scale, scale);
		try {
			if (collapsed && isTrade()) {
				renderCollapsed(g, font, mx, my);
			} else {
				renderPanel(g, font, mx, my);
			}
		} finally {
			g.pose().popMatrix();
		}
		if (!collapsed) {
			highlightSlots(g);
		}
		if (pendingTooltip != null) {
			drawTooltip(g, font, pendingTooltip, guiMx, guiMy);
		}
	}

	/**
	 * Draws a tooltip box now (the deferred tooltip pass has already run), shrunk to fit on short screens.
	 * Drawn by hand because the vanilla tooltip API differs between Minecraft versions.
	 */
	private void drawTooltip(GuiGraphicsExtractor g, Font font, List<Component> lines, int guiMx, int guiMy) {
		List<FormattedCharSequence> seqs = new ArrayList<>();
		int tw = 0;
		for (Component c : lines) {
			FormattedCharSequence seq = c.getVisualOrderText();
			seqs.add(seq);
			tw = Math.max(tw, font.width(seq));
		}
		int th = seqs.size() * 10 + (seqs.size() > 1 ? 2 : 0) - 1;
		float ts = Math.min(1f, (screen.height - 12) / (float) (th + 8));
		int maxW = (int) (screen.width / ts);
		int maxH = (int) (screen.height / ts);
		int mx = (int) (guiMx / ts);
		int my = (int) (guiMy / ts);
		int tx = mx + 12;
		if (tx + tw + 6 > maxW) {
			tx = Math.max(4, mx - 16 - tw);
		}
		int ty = Math.max(5, Math.min(my - 12, maxH - th - 5));
		g.nextStratum();
		g.pose().pushMatrix();
		g.pose().scale(ts, ts);
		g.fill(tx - 4, ty - 4, tx + tw + 4, ty + th + 4, 0xF0100010);
		g.outline(tx - 4, ty - 4, tw + 8, th + 8, 0xFF2A0A5A);
		g.outline(tx - 3, ty - 3, tw + 6, th + 6, 0x505000FF);
		int ly = ty;
		for (int i = 0; i < seqs.size(); i++) {
			g.text(font, seqs.get(i), tx, ly, Draw.WHITE, true);
			ly += 10 + (i == 0 ? 2 : 0);
		}
		g.pose().popMatrix();
	}

	private void renderPanel(GuiGraphicsExtractor g, Font font, int mx, int my) {
		LowballConfig cfg = LowballConfig.get();
		if (isTrade()) {
			refreshState();
		}
		if (mode == Mode.DETAIL) {
			refreshDetail();
			if (mode != Mode.DETAIL) {
				return;
			}
		}
		TradeState s = state;
		boolean compact = cfg.compactRows;
		int rowH = compact ? 22 : 34;
		int bodyW = w - PAD * 2 - 3;
		int bodyHeight = switch (mode) {
			case SETTINGS -> settings.height();
			case DETAIL -> breakdown == null ? 20 : breakdown.height(font, bodyW) + 4;
			case LIST -> {
				int rows = s.theirs.size() + (cfg.showYourSide ? s.yours.size() : 0);
				int headers = 1 + (cfg.showYourSide && !s.yours.isEmpty() ? 1 : 0);
				yield rows * rowH + headers * (LINE + 4) + (s.theirs.isEmpty() ? LINE * 3 : 0) + 2;
			}
		};
		int footer = isTrade() ? footerHeight(s) : inspectFooterHeight();
		layout(bodyHeight, footer);

		Draw.panel(g, x, y, w, h);
		g.fill(x + 1, y + 2, x + w - 1, y + headerHeight() - 1, Draw.BG_HEADER);
		renderHeader(g, font, mx, my);

		g.enableScissor(x + 1, bodyTop, x + w - 1, bodyBottom);
		switch (mode) {
			case SETTINGS -> {
				String hint = settings.render(g, font, x + PAD, bodyTop - scroll, w - PAD * 2, mx, my, (x0, y0, x1, y1, click) -> {
					if (y1 > bodyTop && y0 < bodyBottom) {
						hotspots.add(new Hotspot(null, x0, Math.max(y0, bodyTop), x1, Math.min(y1, bodyBottom), click::accept));
					}
				});
				if (hint != null && Draw.inside(mx, my, x, bodyTop, x + w, bodyBottom)) {
					pendingTooltip = new ArrayList<>();
					for (FormattedCharSequence line : font.split(Component.literal(hint), 180)) {
						pendingTooltip.add(Component.literal("§7" + toPlain(line)));
					}
				}
			}
			case DETAIL -> {
				if (breakdown != null) {
					breakdown.render(g, font, x + PAD, bodyTop + 2 - scroll, bodyW, bodyTop, bodyBottom);
				}
			}
			case LIST -> renderList(g, font, s, rowH, compact, mx, my);
		}
		g.disableScissor();
		if (maxScroll > 0) {
			int track = bodyBottom - bodyTop;
			int thumb = Math.max(10, track * track / (track + maxScroll));
			int ty = bodyTop + (track - thumb) * scroll / maxScroll;
			g.fill(x + w - 3, bodyTop, x + w - 1, bodyBottom, 0x20FFFFFF);
			g.fill(x + w - 3, ty, x + w - 1, ty + thumb, 0xA0FFFFFF);
		}

		if (isTrade()) {
			renderFooter(g, font, s, mx, my);
		} else {
			renderInspectFooter(g, font, mx, my);
		}
		if (hoveredEntry != null && Draw.inside(mx, my, x, bodyTop, x + w, bodyBottom)) {
			pendingTooltip = summaryTooltip(hoveredEntry);
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
		String label = "◀ Lowball";
		int bw = font.width(label) + 10;
		int by = (int) (ScreenAccess.top(screen) / scale);
		int bx = (int) Math.ceil((ScreenAccess.left(screen) + ScreenAccess.imageWidth(screen) + GAP) / scale);
		if (bx + bw > screenW) {
			label = "Lowball ▶";
			bx = (int) ((ScreenAccess.left(screen) - GAP) / scale) - bw;
		}
		boolean hover = Draw.inside(mx, my, bx, by, bx + bw, by + 14);
		Draw.button(g, font, bx, by, bw, 14, label, hover, Draw.GOLD);
		hotspots.add(new Hotspot("expand", bx, by, bx + bw, by + 14, (b, sh) -> collapsed = false));
	}

	private void renderHeader(GuiGraphicsExtractor g, Font font, int mx, int my) {
		LowballConfig cfg = LowballConfig.get();
		int ty = y + PAD;
		int ix = x + w - PAD - 9;
		if (mode == Mode.DETAIL) {
			boolean canGoBack = isTrade();
			String back = canGoBack ? "◀ Back" : "✕ Close";
			int bw = font.width(back) + 6;
			boolean hb = Draw.inside(mx, my, x + PAD - 2, ty - 2, x + PAD - 2 + bw, ty + 9);
			if (hb) {
				g.fill(x + PAD - 2, ty - 2, x + PAD - 2 + bw, ty + 9, Draw.HOVER);
			}
			g.text(font, back, x + PAD + 1, ty, hb ? Draw.GOLD : Draw.TEXT, true);
			hotspots.add(new Hotspot("back", x + PAD - 2, ty - 2, x + PAD - 2 + bw, ty + 9, (b, sh) -> {
				if (canGoBack) {
					back();
				} else {
					detailStack = null;
					mode = Mode.LIST;
				}
			}));
			g.text(font, "§6§lBreakdown", x + PAD + bw + 6, ty, Draw.WHITE, true);
		} else {
			g.text(font, "§6§lLowball", x + PAD, ty, Draw.WHITE, true);
			int titleW = font.width("§6§lLowball") + 4;
			String vs = Draw.ellipsize(font, "vs " + partner, w - PAD * 2 - titleW - 30);
			g.text(font, vs, x + PAD + titleW, ty, Draw.GRAY, true);
			iconButton(g, font, ix, ty - 1, "–", mx, my, "collapse", (b, sh) -> collapsed = true);
			iconButton(g, font, ix - 12, ty - 1, mode == Mode.SETTINGS ? "§e⚙" : "⚙", mx, my, "settings", (b, sh) -> {
				mode = mode == Mode.SETTINGS ? Mode.LIST : Mode.SETTINGS;
				scroll = 0;
			});
		}

		// preset line
		int py = ty + LINE + 2;
		String preset = cfg.preset.label + " §f" + Math.round(cfg.basePercent()) + "%";
		int bx = x + PAD;
		iconButton(g, font, bx, py - 1, "◀", mx, my, "preset-prev", (b, sh) -> {
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
		iconButton(g, font, labelX + labelW, py - 1, "▶", mx, my, "preset-next", (b, sh) -> {
			cfg.preset = cfg.preset.next();
			cfg.save();
		});
		int rx = x + w - PAD - 9;
		iconButton(g, font, rx, py - 1, "+", mx, my, "plus", (b, sh) -> {
			cfg.setBasePercent(cfg.basePercent() + (sh ? 5 : 1));
			cfg.save();
		});
		iconButton(g, font, rx - 12, py - 1, "-", mx, my, "minus", (b, sh) -> {
			cfg.setBasePercent(cfg.basePercent() - (sh ? 5 : 1));
			cfg.save();
		});
		g.horizontalLine(x + 1, x + w - 2, y + headerHeight() - 1, Draw.BORDER);
	}

	private void iconButton(GuiGraphicsExtractor g, Font font, int bx, int by, String label, int mx, int my, @Nullable String id, Click click) {
		boolean hover = Draw.inside(mx, my, bx, by, bx + 10, by + 10);
		if (hover) {
			g.fill(bx, by, bx + 10, by + 10, Draw.HOVER);
		}
		g.centeredText(font, label, bx + 5, by + 1, hover ? Draw.GOLD : Draw.GRAY);
		hotspots.add(new Hotspot(id, bx, by, bx + 10, by + 10, click));
	}

	// ------------------------------------------------------------------ list

	private void renderList(GuiGraphicsExtractor g, Font font, TradeState s, int rowH, boolean compact, int mx, int my) {
		LowballConfig cfg = LowballConfig.get();
		int cy = bodyTop - scroll + 2;
		cy = sectionLabel(g, font, "They give", s.theirs.size(), s.theirItemsValue, cy);
		if (s.theirs.isEmpty()) {
			g.text(font, "§8Waiting for their items…", x + PAD, cy + 4, Draw.WHITE, false);
			g.text(font, "§8Tip: press " + keyName() + " on any item for a breakdown", x + PAD, cy + 15, Draw.WHITE, false);
			cy += LINE * 3;
		}
		for (int i = 0; i < s.theirs.size(); i++) {
			renderEntry(g, font, s.theirs.get(i), cy, rowH, compact, mx, my, true, i);
			cy += rowH;
		}
		if (cfg.showYourSide && !s.yours.isEmpty()) {
			cy = sectionLabel(g, font, "You give", s.yours.size(), s.yourCoins + s.yourItemsValue, cy);
			for (int i = 0; i < s.yours.size(); i++) {
				renderEntry(g, font, s.yours.get(i), cy, rowH, compact, mx, my, false, 100 + i);
				cy += rowH;
			}
		}
	}

	private int sectionLabel(GuiGraphicsExtractor g, Font font, String label, int count, double total, int cy) {
		g.text(font, label + " §8(" + count + ")", x + PAD, cy + 2, Draw.GRAY, true);
		if (total > 0) {
			Draw.rightText(g, font, "§7" + Fmt.coins(total), x + w - PAD - 3, cy + 2, Draw.WHITE);
		}
		g.horizontalLine(x + PAD, x + w - PAD - 3, cy + LINE + 2, Draw.SEPARATOR);
		return cy + LINE + 4;
	}

	private static String keyName() {
		return inspectKey == null ? "the inspect key" : inspectKey.getTranslatedKeyMessage().getString();
	}

	private void renderEntry(GuiGraphicsExtractor g, Font font, TradeEntry e, int ey, int rowH, boolean compact, int mx, int my, boolean theirs, int index) {
		if (ey + rowH < bodyTop || ey > bodyBottom) {
			return;
		}
		boolean hover = Draw.inside(mx, my, x + 1, ey, x + w - 1, ey + rowH - 1) && Draw.inside(mx, my, x, bodyTop, x + w, bodyBottom);
		Slot hoveredSlot = ScreenAccess.hoveredSlot(screen);
		boolean linked = hoveredSlot != null && hoveredSlot == e.slot();
		if (hover || linked) {
			g.fill(x + 1, ey, x + w - 1, ey + rowH - 1, Draw.BG_ROW_HOVER);
		}
		g.fill(x + 1, ey + 1, x + 3, ey + rowH - 2, nameColor(e.stack()));
		Valuation v = e.valuation();
		if (hover) {
			hoveredEntry = e;
		}
		if (v != null && v.known() && ey + rowH - 1 > bodyTop && ey < bodyBottom) {
			hotspots.add(new Hotspot("row" + index, x + 1, Math.max(ey, bodyTop), x + w - 1, Math.min(ey + rowH - 1, bodyBottom),
					(b, sh) -> openDetail(e.stack(), theirs)));
		}
		int iconY = ey + (rowH - 1 - 16) / 2;
		g.item(e.stack(), x + PAD + 1, iconY);
		g.itemDecorations(font, e.stack(), x + PAD + 1, iconY);
		int tx = x + PAD + 22;
		int tw = w - PAD * 2 - 22 - 3;
		int l1 = ey + 3;
		int l2 = l1 + LINE;
		int l3 = l2 + LINE;
		Offer o = e.offer();

		if (e.isCoins()) {
			g.text(font, "§6" + Fmt.coins(e.coins()) + " coins", tx, l1, Draw.WHITE, true);
			g.text(font, "§8" + Fmt.full(e.coins()), tx, l2, Draw.WHITE, false);
			return;
		}
		// right side: offer (their side) or worth (yours)
		String right = v != null && v.known() ? (theirs ? Fmt.coins(o.totalOffer()) : Fmt.coins(v.totalValue())) : "";
		int rightW = right.isEmpty() ? 0 : font.width(right) + 6;
		int badgeW = 0;
		if (v != null && v.exotic() != null) {
			badgeW = font.width(v.exotic().type().label) + 8;
		}
		boolean warn = v != null && !v.warnings().isEmpty();
		int warnW = warn ? 8 : 0;
		drawName(g, font, e.stack(), tx, l1, tw - rightW - badgeW - warnW);
		if (rightW > 0) {
			Draw.pill(g, font, tx + tw - rightW + 1, l1 - 1, right, theirs ? 0xFF1E5631 : 0xFF2A2E44, theirs ? Draw.GREEN : Draw.TEXT);
		}
		int bxp = tx + tw - rightW - badgeW - warnW;
		if (badgeW > 0) {
			Draw.pill(g, font, bxp + 1, l1 - 1, v.exotic().type().label, 0xFF6A1B5A, Draw.PINK);
		}
		if (warn) {
			g.text(font, "§c⚠", tx + tw - rightW - warnW + 1, l1, Draw.WHITE, false);
		}

		if (v == null) {
			g.text(font, "§8Not a SkyBlock item", tx, l2, Draw.WHITE, false);
			return;
		}
		if (!v.known()) {
			g.text(font, "§cNo price data §8" + Draw.ellipsize(font, v.item().key, tw - 70), tx, l2, Draw.WHITE, false);
			return;
		}
		g.text(font, statsLine(v), tx, l2, Draw.WHITE, false);
		if (!compact) {
			String l3text = "§7Worth §f" + Fmt.coins(v.totalValue())
					+ (v.upgradesCredited() > 0 ? " §8(" + Fmt.coins(v.unitBase() * v.item().count) + " + " + Fmt.coins(v.upgradesCredited() * v.item().count) + ")" : "");
			g.text(font, l3text, tx, l3, Draw.WHITE, false);
			if (theirs) {
				Draw.bar(g, tx, l3 + 10, tw, 2, o.percent(), 0xFF3FAF6A);
			}
		}
	}

	private static int nameColor(ItemStack stack) {
		Component name = stack.getHoverName();
		TextColor c = name.getStyle().getColor();
		if (c == null) {
			for (Component sib : name.getSiblings()) {
				if (sib.getStyle().getColor() != null && !sib.getString().isBlank()) {
					c = sib.getStyle().getColor();
					break;
				}
			}
		}
		if (c == null) {
			String raw = name.getString();
			if (raw.length() > 1 && raw.charAt(0) == '§') {
				int rgb = dev.lowball.helper.client.Compat.legacyColor(raw.charAt(1));
				if (rgb >= 0) {
					return 0xFF000000 | rgb;
				}
			}
			return Draw.DARK_GRAY;
		}
		return 0xFF000000 | c.getValue();
	}

	private static void drawName(GuiGraphicsExtractor g, Font font, ItemStack stack, int tx, int ty, int maxW) {
		List<FormattedCharSequence> lines = font.split(stack.getHoverName(), Math.max(20, maxW));
		if (!lines.isEmpty()) {
			g.text(font, lines.get(0), tx, ty, Draw.WHITE, true);
		}
	}

	private static String statsLine(Valuation v) {
		String vol = Double.isNaN(v.dailyVolume()) ? "§8?/d" : colorCode(v) + Fmt.volume(v.dailyVolume()) + "/d";
		if (v.bazaar()) {
			return "§7BZ §f" + Fmt.coins(v.unitBase()) + " §8· " + vol;
		}
		String listed = v.listed() < 0 ? "§8? AH" : "§e" + v.listed() + " §7AH";
		AuctionStats ah = v.auction();
		String price;
		if (v.exotic() != null && !Double.isNaN(v.exotic().estimate())) {
			price = "§dExo §f" + Fmt.coins(v.exotic().estimate());
		} else if (ah != null && !Double.isNaN(ah.lowest())) {
			price = "§7LBIN §f" + Fmt.coins(ah.lowest());
		} else {
			price = "§7" + shortSource(v.baseSource()) + " §f" + Fmt.coins(v.unitBase());
		}
		return price + " §8· " + vol + " §8· " + listed;
	}

	private static String shortSource(String src) {
		if (src.contains("median")) {
			return "Med";
		}
		if (src.startsWith("Craft")) {
			return "Craft";
		}
		return src.startsWith("NPC") ? "NPC" : "Val";
	}

	private static String colorCode(Valuation v) {
		return switch (v.liquidity()) {
			case FAST -> "§a";
			case OK -> "§e";
			case SLOW -> "§6";
			case ILLIQUID -> "§c";
			default -> "§7";
		};
	}

	// ------------------------------------------------------------------ footer

	private int footerHeight(@Nullable TradeState s) {
		int lines = 3;
		if (s != null && (s.yourCoins > 0 || s.yourItemsValue > 0)) {
			lines += 2;
		}
		if (s != null && s.unknownItems > 0) {
			lines++;
		}
		return 4 + lines * LINE + 8 + 14 + 3 + LINE;
	}

	private void renderFooter(GuiGraphicsExtractor g, Font font, TradeState s, int mx, int my) {
		int fy = bodyBottom;
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

		double paying = s.yourCoins + s.yourItemsValue;
		if (paying > 0) {
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

		// price bar: suggested (green tick) and what you pay (white tick) against total worth
		int barW = w - PAD * 2;
		Draw.bar(g, lx, ly + 2, barW, 3, s.suggestedPercent(), 0xFF2E7D4F);
		if (paying > 0 && s.theirItemsValue > 0) {
			int px = lx + (int) Math.round(barW * Math.min(1, paying / s.theirItemsValue));
			g.fill(px - 1, ly, px + 1, ly + 7, paying <= s.suggestedOffer * 1.001 ? Draw.GREEN : Draw.RED);
		}
		ly += 8;

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

	private int inspectFooterHeight() {
		return 4 + 14 + 3 + LINE;
	}

	private void renderInspectFooter(GuiGraphicsExtractor g, Font font, int mx, int my) {
		int fy = bodyBottom;
		g.horizontalLine(x + 1, x + w - 2, fy, Draw.BORDER);
		g.fill(x + 1, fy + 1, x + w - 1, y + h - 1, Draw.BG_HEADER);
		int lx = x + PAD;
		int ly = fy + 4;
		int bw = (w - PAD * 2 - 4) / 2;
		double offer = detailOfferResult != null ? detailOfferResult.totalOffer() : 0;
		double worth = detailValuation != null ? detailValuation.totalValue() : 0;
		boolean h1 = offer > 0 && Draw.inside(mx, my, lx, ly, lx + bw, ly + 14);
		Draw.button(g, font, lx, ly, bw, 14, "Copy offer", h1, offer > 0 ? Draw.GREEN : Draw.DARK_GRAY);
		boolean h2 = worth > 0 && Draw.inside(mx, my, lx + bw + 4, ly, lx + bw * 2 + 4, ly + 14);
		Draw.button(g, font, lx + bw + 4, ly, bw, 14, "Copy worth", h2, worth > 0 ? Draw.WHITE : Draw.DARK_GRAY);
		if (offer > 0) {
			hotspots.add(new Hotspot("copy", lx, ly, lx + bw, ly + 14, (b, sh) -> SignAutofill.copy(offer)));
		}
		if (worth > 0) {
			hotspots.add(new Hotspot("copy-worth", lx + bw + 4, ly, lx + bw * 2 + 4, ly + 14, (b, sh) -> SignAutofill.copy(worth)));
		}
		g.text(font, Draw.ellipsize(font, statusLine(), w - PAD * 2), lx, ly + 17, Draw.DARK_GRAY, false);
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
		Slot slot = null;
		if (hoveredEntry != null) {
			slot = hoveredEntry.slot();
		} else if (mode == Mode.DETAIL && detailStack != null) {
			for (Slot s : screen.getMenu().slots) {
				if (s.getItem() == detailStack) {
					slot = s;
					break;
				}
			}
		}
		if (slot == null) {
			return;
		}
		int sx = ScreenAccess.left(screen) + slot.x;
		int sy = ScreenAccess.top(screen) + slot.y;
		g.fill(sx, sy, sx + 16, sy + 16, 0x5055FF88);
		g.outline(sx - 1, sy - 1, 18, 18, Draw.GREEN);
	}

	// ------------------------------------------------------------------ hover summary

	private static List<Component> summaryTooltip(TradeEntry e) {
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
		if (!v.known()) {
			lines.add(Component.literal("§cNo price data yet §8(" + v.item().key + ")"));
			return lines;
		}
		lines.add(Component.literal("§7Base §f" + Fmt.coins(v.unitBase()) + " §8" + v.baseSource()));
		if (v.upgradesRaw() > 0) {
			lines.add(Component.literal("§7Upgrades §f+" + Fmt.coins(v.upgradesCredited()) + " §8of " + Fmt.coins(v.upgradesRaw())));
		}
		if (v.craft() != null && v.craft().complete()) {
			lines.add(Component.literal("§7Craft cost §f" + Fmt.coins(v.craft().total())));
		}
		lines.add(Component.literal("§7Worth §f" + Fmt.coins(v.totalValue())
				+ (TradeMenu.isTheirs(e.slot().index) ? " §8→ §aoffer " + Fmt.coins(e.offer().totalOffer()) + " §7(" + Fmt.percent(e.offer().percent()) + ")" : "")));
		for (String w : v.warnings()) {
			lines.add(Component.literal("§c⚠ " + w));
		}
		lines.add(Component.literal("§eClick for the full breakdown"));
		return lines;
	}

	// ------------------------------------------------------------------ input

	/** Center of a named control in GUI coordinates (for automated client tests), or null. */
	public static int @Nullable [] controlCenter(AbstractContainerScreen<?> screen, String id) {
		LowballPanel panel = PANELS.get(screen);
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

	/** For automated client tests: open the breakdown of a stack as if the inspect key was pressed. */
	public static void inspect(AbstractContainerScreen<?> screen, ItemStack stack) {
		LowballPanel panel = PANELS.get(screen);
		if (panel != null) {
			panel.openDetail(stack, true);
		}
	}

	/** For automated client tests: scroll the panel body. */
	public static void scrollBy(AbstractContainerScreen<?> screen, int pixels) {
		LowballPanel panel = PANELS.get(screen);
		if (panel != null) {
			panel.scroll = Math.max(0, Math.min(panel.maxScroll, panel.scroll + pixels));
		}
	}

	private boolean click(double guiMx, double guiMy, int button, boolean shift) {
		if (!visible()) {
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
		return !(collapsed && isTrade()) && Draw.inside(mx, my, x, y, x + w, y + h);
	}

	private boolean scrolled(double guiMx, double guiMy, double amount) {
		if (!visible()) {
			return false;
		}
		double mx = guiMx / scale;
		double my = guiMy / scale;
		if ((collapsed && isTrade()) || !Draw.inside(mx, my, x, y, x + w, y + h)) {
			return false;
		}
		scroll = (int) Math.max(0, Math.min(maxScroll, scroll - amount * 18));
		return true;
	}
}
