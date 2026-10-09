package dev.lowball.helper.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.LowballHelper;

/**
 * Small differences between Minecraft 26.1, 26.2 and 26.3, looked up once at runtime so one source tree builds for
 * every version (26.2 moved screen handling from {@link Minecraft} to {@link Gui}).
 */
public final class Compat {
	private static final @Nullable Method MC_SET_SCREEN = method(Minecraft.class, "setScreen", Screen.class);
	private static final @Nullable Method GUI_SET_SCREEN = method(Gui.class, "setScreen", Screen.class);
	private static final @Nullable Field MC_SCREEN = field(Minecraft.class, "screen");
	private static final @Nullable Method GUI_SCREEN = method(Gui.class, "screen");

	private Compat() {
	}

	private static @Nullable Method method(Class<?> c, String name, Class<?>... args) {
		try {
			return c.getMethod(name, args);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	private static @Nullable Field field(Class<?> c, String name) {
		try {
			return c.getField(name);
		} catch (ReflectiveOperationException e) {
			return null;
		}
	}

	public static void setScreen(@Nullable Screen screen) {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (MC_SET_SCREEN != null) {
				MC_SET_SCREEN.invoke(mc, screen);
			} else if (GUI_SET_SCREEN != null) {
				GUI_SET_SCREEN.invoke(mc.gui, screen);
			} else {
				LowballHelper.LOGGER.error("Lowball Helper: no way to open screens on this Minecraft version");
			}
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not open screen", e.getCause() != null ? e.getCause() : e);
		}
	}

	public static @Nullable Screen screen() {
		Minecraft mc = Minecraft.getInstance();
		try {
			if (MC_SCREEN != null) {
				return (Screen) MC_SCREEN.get(mc);
			}
			if (GUI_SCREEN != null) {
				return (Screen) GUI_SCREEN.invoke(mc.gui);
			}
		} catch (ReflectiveOperationException ignored) {
			// fall through
		}
		return null;
	}

	/** RGB of a legacy formatting color code (§a etc.), or -1. */
	public static int legacyColor(char code) {
		return switch (Character.toLowerCase(code)) {
			case '0' -> 0x000000;
			case '1' -> 0x0000AA;
			case '2' -> 0x00AA00;
			case '3' -> 0x00AAAA;
			case '4' -> 0xAA0000;
			case '5' -> 0xAA00AA;
			case '6' -> 0xFFAA00;
			case '7' -> 0xAAAAAA;
			case '8' -> 0x555555;
			case '9' -> 0x5555FF;
			case 'a' -> 0x55FF55;
			case 'b' -> 0x55FFFF;
			case 'c' -> 0xFF5555;
			case 'd' -> 0xFF55FF;
			case 'e' -> 0xFFFF55;
			case 'f' -> 0xFFFFFF;
			default -> -1;
		};
	}
}
