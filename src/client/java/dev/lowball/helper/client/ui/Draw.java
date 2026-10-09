package dev.lowball.helper.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Theme and small drawing helpers shared by every Lowball Helper UI. Colors are ARGB. */
public final class Draw {
	public static final int BG = 0xF00E0F16;
	public static final int BG_HEADER = 0xF8161824;
	public static final int BG_CARD = 0xFF151722;
	public static final int BG_ROW_HOVER = 0x26FFFFFF;
	public static final int BORDER = 0xFF2E3248;
	public static final int BORDER_LIGHT = 0xFF444A6A;
	public static final int ACCENT = 0xFFFFB02E;
	public static final int SEPARATOR = 0x30FFFFFF;
	public static final int HOVER = 0x30FFFFFF;
	public static final int BUTTON = 0xFF1F2233;
	public static final int BUTTON_HOVER = 0xFF2C3150;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int TEXT = 0xFFE6E8F0;
	public static final int GRAY = 0xFFA3A8BD;
	public static final int DARK_GRAY = 0xFF62677F;
	public static final int GREEN = 0xFF5CF08A;
	public static final int YELLOW = 0xFFFFE066;
	public static final int GOLD = 0xFFFFB02E;
	public static final int RED = 0xFFFF6464;
	public static final int AQUA = 0xFF5CE1F0;
	public static final int PINK = 0xFFFF7AD9;

	private Draw() {
	}

	public static boolean inside(double mx, double my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		// soft shadow + body + border
		g.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x60000000);
		g.fill(x, y, x + w, y + h, BG);
		g.outline(x, y, w, h, BORDER);
		g.fill(x + 1, y + 1, x + w - 1, y + 2, ACCENT);
	}

	public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, boolean hovered, int textColor) {
		g.fill(x, y, x + w, y + h, hovered ? BUTTON_HOVER : BUTTON);
		g.outline(x, y, w, h, hovered ? ACCENT : BORDER_LIGHT);
		g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, textColor);
	}

	/** Small rounded-looking label, returns its width. */
	public static int pill(GuiGraphicsExtractor g, Font font, int x, int y, String text, int bg, int fg) {
		int w = font.width(text) + 6;
		g.fill(x + 1, y, x + w - 1, y + 10, bg);
		g.fill(x, y + 1, x + w, y + 9, bg);
		g.text(font, text, x + 3, y + 1, fg, false);
		return w;
	}

	/** Horizontal bar: {@code fraction} filled with {@code color}. */
	public static void bar(GuiGraphicsExtractor g, int x, int y, int w, int h, double fraction, int color) {
		g.fill(x, y, x + w, y + h, 0xFF262A3C);
		int f = (int) Math.round(w * Math.max(0, Math.min(1, fraction)));
		if (f > 0) {
			g.fill(x, y, x + f, y + h, color);
		}
	}

	public static void swatch(GuiGraphicsExtractor g, int x, int y, int size, int rgb) {
		g.fill(x, y, x + size, y + size, 0xFF000000);
		g.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF000000 | rgb);
	}

	public static void rightText(GuiGraphicsExtractor g, Font font, String text, int rightX, int y, int color) {
		g.text(font, text, rightX - font.width(text), y, color, true);
	}

	public static String ellipsize(Font font, String text, int maxWidth) {
		if (font.width(text) <= maxWidth) {
			return text;
		}
		String dots = "…";
		return font.plainSubstrByWidth(text, Math.max(0, maxWidth - font.width(dots))) + dots;
	}

	public static int liquidityColor(dev.lowball.helper.valuation.Liquidity l) {
		return switch (l) {
			case FAST -> GREEN;
			case OK -> YELLOW;
			case SLOW -> GOLD;
			case ILLIQUID -> RED;
			default -> GRAY;
		};
	}
}
