package dev.lowball.helper.client;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.jspecify.annotations.Nullable;

import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.util.Text;

public final class Items {
	private static final Pattern COINS = Pattern.compile("(?i)^([0-9][0-9.,]*\\s*[kmb]?)\\s*coins?$");

	private Items() {
	}

	public static @Nullable SkyblockItem of(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return null;
		}
		return SkyblockItem.of(data.copyTag(), stack.getHoverName().getString(), stack.getCount());
	}

	/** Coins shown as an item in the trade menu ("1.5M coins"). NaN if the stack isn't coins. */
	public static double coins(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Double.NaN;
		}
		String name = Text.strip(stack.getHoverName().getString()).trim();
		Matcher m = COINS.matcher(name);
		if (!m.matches()) {
			return Double.NaN;
		}
		return Fmt.parse(m.group(1).replace(" ", ""));
	}
}
