package dev.lowball.helper.client.trade;

import net.minecraft.client.KeyMapping;
import org.jspecify.annotations.Nullable;

/** Holds the inspect key so tooltips and the panel can show its current binding. */
public final class LowballPanelKey {
	private static @Nullable KeyMapping key;

	private LowballPanelKey() {
	}

	static void set(KeyMapping k) {
		key = k;
	}

	public static String name() {
		return key == null ? "V" : key.getTranslatedKeyMessage().getString();
	}
}
