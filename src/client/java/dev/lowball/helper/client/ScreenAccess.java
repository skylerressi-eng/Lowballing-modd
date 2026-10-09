package dev.lowball.helper.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.LowballHelper;

/**
 * Reads the few non-public screen fields the mod needs. Uses reflection (Minecraft 26.x ships with readable names)
 * instead of Mixins so the mod works on clients that restrict Mixins, like Lunar Client, and falls back to computing
 * the values when reflection isn't possible.
 */
public final class ScreenAccess {
	private static final @Nullable Field LEFT = field(AbstractContainerScreen.class, "leftPos");
	private static final @Nullable Field TOP = field(AbstractContainerScreen.class, "topPos");
	private static final @Nullable Field IMAGE_W = field(AbstractContainerScreen.class, "imageWidth");
	private static final @Nullable Field IMAGE_H = field(AbstractContainerScreen.class, "imageHeight");
	private static final @Nullable Field HOVERED = field(AbstractContainerScreen.class, "hoveredSlot");
	private static final @Nullable Field SIGN_MESSAGES = field(AbstractSignEditScreen.class, "messages");
	private static final @Nullable Field SIGN_LINE = field(AbstractSignEditScreen.class, "line");
	private static final @Nullable Method SIGN_SET = method(AbstractSignEditScreen.class, "setMessage", String.class);

	private ScreenAccess() {
	}

	private static @Nullable Field field(Class<?> owner, String name) {
		try {
			Field f = owner.getDeclaredField(name);
			f.setAccessible(true);
			return f;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LowballHelper.LOGGER.warn("Lowball Helper: {}.{} not accessible, using fallback", owner.getSimpleName(), name);
			return null;
		}
	}

	private static @Nullable Method method(Class<?> owner, String name, Class<?>... args) {
		try {
			Method m = owner.getDeclaredMethod(name, args);
			m.setAccessible(true);
			return m;
		} catch (ReflectiveOperationException | RuntimeException e) {
			LowballHelper.LOGGER.warn("Lowball Helper: {}.{}() not accessible, using fallback", owner.getSimpleName(), name);
			return null;
		}
	}

	private static int intOr(@Nullable Field f, Object o, int fallback) {
		if (f != null) {
			try {
				return f.getInt(o);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// fall back
			}
		}
		return fallback;
	}

	public static int imageWidth(AbstractContainerScreen<?> s) {
		return intOr(IMAGE_W, s, 176);
	}

	public static int imageHeight(AbstractContainerScreen<?> s) {
		int fallback = s.getMenu() instanceof ChestMenu chest ? 114 + chest.getRowCount() * 18 : 166;
		return intOr(IMAGE_H, s, fallback);
	}

	public static int left(AbstractContainerScreen<?> s) {
		return intOr(LEFT, s, (s.width - imageWidth(s)) / 2);
	}

	public static int top(AbstractContainerScreen<?> s) {
		return intOr(TOP, s, (s.height - imageHeight(s)) / 2);
	}

	public static @Nullable Slot hoveredSlot(AbstractContainerScreen<?> s) {
		if (HOVERED != null) {
			try {
				return (Slot) HOVERED.get(s);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// fall back
			}
		}
		Minecraft mc = Minecraft.getInstance();
		double mx = mc.mouseHandler.getScaledXPos(mc.getWindow());
		double my = mc.mouseHandler.getScaledYPos(mc.getWindow());
		int left = left(s);
		int top = top(s);
		for (Slot slot : s.getMenu().slots) {
			int x = left + slot.x;
			int y = top + slot.y;
			if (slot.isActive() && mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17) {
				return slot;
			}
		}
		return null;
	}

	/** Sign lines as shown, or null when they can't be read. */
	public static String @Nullable [] signLines(AbstractSignEditScreen s) {
		if (SIGN_MESSAGES != null) {
			try {
				return (String[]) SIGN_MESSAGES.get(s);
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// unknown
			}
		}
		return null;
	}

	/** Replaces the first sign line with {@code text}. */
	public static void setFirstSignLine(AbstractSignEditScreen s, String text) {
		if (SIGN_SET != null && SIGN_LINE != null) {
			try {
				SIGN_LINE.setInt(s, 0);
				SIGN_SET.invoke(s, text);
				return;
			} catch (ReflectiveOperationException | RuntimeException ignored) {
				// fall back to typing
			}
		}
		// the cursor starts on the first line: type it in like a player would
		text.codePoints().forEach(cp -> s.charTyped(new CharacterEvent(cp)));
	}
}
