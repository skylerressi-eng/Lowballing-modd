package dev.lowball.helper.client.trade;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ChestMenu;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.util.Text;

/**
 * The Hypixel trade window: a 5-row chest titled "You" + padding + partner name.
 * Columns 0-3 of the first 4 rows are your offer, column 4 is the divider, columns 5-8 are theirs.
 */
public final class TradeMenu {
	private static final Pattern TITLE = Pattern.compile("^You {2,}(\\S{1,32})$");

	private TradeMenu() {
	}

	public static boolean is(@Nullable Screen screen) {
		return partner(screen) != null;
	}

	public static @Nullable String partner(@Nullable Screen screen) {
		if (screen == null) {
			return null;
		}
		if (!(screen instanceof ContainerScreen cs) || !(cs.getMenu() instanceof ChestMenu menu) || menu.getRowCount() != 5) {
			return null;
		}
		Matcher m = TITLE.matcher(Text.strip(screen.getTitle().getString()).replace(' ', ' ').trim());
		return m.matches() ? m.group(1) : null;
	}

	public static boolean isYours(int slot) {
		return slot >= 0 && slot < 36 && slot % 9 < 4;
	}

	public static boolean isTheirs(int slot) {
		return slot >= 0 && slot < 36 && slot % 9 > 4;
	}
}
