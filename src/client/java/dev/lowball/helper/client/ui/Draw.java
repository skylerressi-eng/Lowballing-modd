package dev.lowball.helper.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Small drawing helpers shared by the panel and settings. Colors are ARGB. */
public final class Draw {
	public static final int BG = 0xE8101016;
	public static final int BG_HEADER = 0xF01A1A26;
	public static final int BORDER = 0xFF3C3C5A;
	public static final int ACCENT = 0xFFFFAA00;
	public static final int SEPARATOR = 0x40FFFFFF;
	public static final int HOVER = 0x30FFFFFF;
	public static final int BUTTON = 0xFF262636;
	public static final int BUTTON_HOVER = 0xFF34344C;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int GRAY = 0xFFAAAAAA;
	public static final int DARK_GRAY = 0xFF666677;
	public static final int GREEN = 0xFF55FF55;
	public static final int YELLOW = 0xFFFFFF55;
	public static final int GOLD = 0xFFFFAA00;
	public static final int RED = 0xFFFF5555;
	public static final int AQUA = 0xFF55FFFF;

	private Draw() {
	}

	public static boolean inside(double mx, double my, int x0, int y0, int x1, int y1) {
		return mx >= x0 && mx < x1 && my >= y0 && my < y1;
	}

	public static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		g.fill(x, y, x + w, y + h, BG);
		g.outline(x, y, w, h, BORDER);
	}

	public static void button(GuiGraphicsExtractor g, Font font, int x, int y, int w, int h, String label, boolean hovered, int textColor) {
		g.fill(x, y, x + w, y + h, hovered ? BUTTON_HOVER : BUTTON);
		g.outline(x, y, w, h, hovered ? ACCENT : BORDER);
		g.centeredText(font, label, x + w / 2, y + (h - 8) / 2, textColor);
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
}
