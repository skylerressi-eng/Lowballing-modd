package dev.lowball.helper.client.ui;

import java.util.List;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Compact settings list drawn inside the trade panel (opening a real screen would close the trade). */
public final class QuickSettings {
	private static final int ROW = 11;

	@FunctionalInterface
	public interface Action {
		void accept(int button, boolean shift);
	}

	@FunctionalInterface
	public interface Sink {
		void add(int x0, int y0, int x1, int y1, Action action);
	}

	private static List<Options.Option> options() {
		return Options.ALL.stream().filter(Options.Option::quick).toList();
	}

	public int height() {
		return options().size() * ROW + 16;
	}

	/** @return description of the hovered option, for the caller to show as a tooltip */
	public @Nullable String render(GuiGraphicsExtractor g, Font font, int x, int y, int width, int mx, int my, Sink sink) {
		String hovered = null;
		int cy = y + 2;
		for (Options.Option o : options()) {
			boolean hover = Draw.inside(mx, my, x - 2, cy - 1, x + width + 2, cy + ROW - 1);
			if (hover) {
				g.fill(x - 2, cy - 1, x + width + 2, cy + ROW - 1, Draw.HOVER);
				hovered = o.description();
			}
			g.text(font, o.label(), x, cy + 1, Draw.GRAY, true);
			Draw.rightText(g, font, o.value().get(), x + width, cy + 1, Draw.WHITE);
			sink.add(x - 2, cy - 1, x + width + 2, cy + ROW - 1, (button, shift) -> o.click(button == 1 || shift));
			cy += ROW;
		}
		g.text(font, "§8Right-click: back · /lowball: more", x, cy + 4, Draw.DARK_GRAY, false);
		return hovered;
	}
}
