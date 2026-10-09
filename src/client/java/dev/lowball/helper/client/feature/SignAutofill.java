package dev.lowball.helper.client.feature;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.network.chat.Component;

import dev.lowball.helper.client.ScreenAccess;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.util.Fmt;

/**
 * Pre-types the suggested offer into the next Hypixel coin sign. It only fills in the text:
 * you still click the coin button and confirm the sign yourself.
 */
public final class SignAutofill {
	private static final long WINDOW_MS = 60_000;
	private static volatile long armedAmount = -1;
	private static volatile long armedAt;

	private SignAutofill() {
	}

	public static boolean armed() {
		return armedAmount > 0 && System.currentTimeMillis() - armedAt < WINDOW_MS;
	}

	public static void copy(double coins) {
		Minecraft mc = Minecraft.getInstance();
		mc.keyboardHandler.setClipboard(Fmt.signAmount(Math.floor(coins)));
		message("§7Copied §a" + Fmt.signAmount(Math.floor(coins)) + " §7to clipboard.");
	}

	public static void arm(double coins) {
		long amount = (long) Math.floor(coins);
		if (amount <= 0) {
			return;
		}
		Minecraft.getInstance().keyboardHandler.setClipboard(Fmt.signAmount(amount));
		if (!LowballConfig.get().signAutofill) {
			message("§7Copied §a" + Fmt.signAmount(amount) + "§7. Sign autofill is off in settings.");
			return;
		}
		armedAmount = amount;
		armedAt = System.currentTimeMillis();
		message("§7Offer §a" + Fmt.coins(amount) + " §7ready. Click the coin button in the trade and it'll be typed in.");
	}

	public static void onScreenInit(Screen screen) {
		if (!(screen instanceof AbstractSignEditScreen) || !armed()) {
			return;
		}
		AbstractSignEditScreen sign = (AbstractSignEditScreen) screen;
		String[] lines = ScreenAccess.signLines(sign);
		if (lines != null) {
			StringBuilder hint = new StringBuilder();
			for (int i = 1; i < lines.length; i++) {
				hint.append(lines[i].toLowerCase(Locale.ROOT)).append(' ');
			}
			String h = hint.toString();
			// Hypixel input signs put "^^^^" under the input line and a prompt below it
			if (!(h.contains("^^^") || h.contains("coin") || h.contains("amount"))) {
				return;
			}
			if (!lines[0].isEmpty()) {
				return;
			}
		}
		ScreenAccess.setFirstSignLine(sign, Long.toString(armedAmount));
		armedAmount = -1;
	}

	private static void message(String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null) {
			mc.player.sendOverlayMessage(Component.literal(text));
		}
	}
}
