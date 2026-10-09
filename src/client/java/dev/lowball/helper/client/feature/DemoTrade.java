package dev.lowball.helper.client.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.DyedItemColor;

import dev.lowball.helper.client.HypixelState;

/**
 * Opens a local, fake trade window filled with real SkyBlock items so the panel can be previewed
 * (and tested) without a trade partner. Nothing is sent to the server.
 */
public final class DemoTrade {
	private DemoTrade() {
	}

	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		HypixelState.enableDemo();
		SimpleContainer c = new SimpleContainer(45);
		ItemStack pane = new ItemStack(Items.GRAY_STAINED_GLASS_PANE);
		pane.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
		for (int row = 0; row < 5; row++) {
			c.setItem(row * 9 + 4, pane.copy());
		}
		for (int i = 36; i < 45; i++) {
			c.setItem(i, pane.copy());
		}
		c.setItem(36, named(Items.GOLD_NUGGET, "§eCoin transaction"));
		c.setItem(39, named(Items.LIME_TERRACOTTA, "§aTrading!"));

		// your side: coins
		c.setItem(0, named(Items.GOLD_INGOT, "§6250M coins"));

		// their side
		c.setItem(5, hyperion());
		c.setItem(6, pet(Items.PLAYER_HEAD, "§7[Lvl 100] §6Ender Dragon", "ENDER_DRAGON", "LEGENDARY", "MINOS_RELIC"));
		c.setItem(7, item(Items.BOW, "§6Terminator", "TERMINATOR"));
		c.setItem(8, item(Items.BOW, "§6Juju Shortbow", "JUJU_SHORTBOW"));
		c.setItem(14, book("ultimate_wise", 5, "§9Enchanted Book"));
		ItemStack recomb = item(Items.PLAYER_HEAD, "§6Recombobulator 3000", "RECOMBOBULATOR_3000");
		recomb.setCount(2);
		c.setItem(15, recomb);
		c.setItem(16, pet(Items.PLAYER_HEAD, "§7[Lvl 200] §6Golden Dragon", "GOLDEN_DRAGON", "LEGENDARY", null));
		c.setItem(17, item(Items.DIAMOND_SWORD, "§9Aspect of the End", "ASPECT_OF_THE_END"));
		// a crystal-dyed exotic
		ItemStack exotic = item(Items.LEATHER_CHESTPLATE, "§6Superior Dragon Chestplate", "SUPERIOR_DRAGON_CHESTPLATE");
		exotic.set(DataComponents.DYED_COLOR, new DyedItemColor(0x1F0030));
		c.setItem(23, exotic);
		ItemStack books = book("ultimate_wise", 5, "§9Enchanted Book");
		CompoundTag bt = books.get(DataComponents.CUSTOM_DATA).copyTag();
		CompoundTag ench = bt.getCompoundOrEmpty("enchantments");
		ench.putInt("sharpness", 6);
		bt.put("enchantments", ench);
		books.set(DataComponents.CUSTOM_DATA, CustomData.of(bt));
		c.setItem(24, books);

		ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x5, 0, mc.player.getInventory(), c, 5);
		mc.setScreen(new ContainerScreen(menu, mc.player.getInventory(), Component.literal("You                  Technoblade")));
	}

	private static ItemStack named(Item item, String name) {
		ItemStack s = new ItemStack(item);
		s.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		return s;
	}

	private static ItemStack item(Item base, String name, String id) {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		return withData(named(base, name), t);
	}

	private static ItemStack withData(ItemStack s, CompoundTag t) {
		s.set(DataComponents.CUSTOM_DATA, CustomData.of(t));
		return s;
	}

	/** The upgraded demo Hyperion, for previews. */
	public static ItemStack hyperion() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "HYPERION");
		t.putInt("rarity_upgrades", 1);
		t.putInt("hot_potato_count", 15);
		t.putInt("upgrade_level", 5);
		t.putString("power_ability_scroll", "SAPPHIRE_POWER_SCROLL");
		CompoundTag ench = new CompoundTag();
		ench.putInt("ultimate_wise", 5);
		ench.putInt("sharpness", 6);
		ench.putInt("critical", 6);
		t.put("enchantments", ench);
		ListTag scrolls = new ListTag();
		scrolls.add(StringTag.valueOf("IMPLOSION_SCROLL"));
		scrolls.add(StringTag.valueOf("SHADOW_WARP_SCROLL"));
		scrolls.add(StringTag.valueOf("WITHER_SHIELD_SCROLL"));
		t.put("ability_scroll", scrolls);
		CompoundTag gems = new CompoundTag();
		gems.putString("SAPPHIRE_0", "PERFECT");
		t.put("gems", gems);
		return withData(named(Items.IRON_SWORD, "§dHeroic Hyperion §6✪✪✪✪✪"), t);
	}

	private static ItemStack pet(Item base, String name, String type, String tier, String held) {
		CompoundTag t = new CompoundTag();
		t.putString("id", "PET");
		t.putString("petInfo", "{\"type\":\"" + type + "\",\"active\":false,\"exp\":0,\"tier\":\"" + tier + "\""
				+ (held != null ? ",\"heldItem\":\"" + held + "\"" : "") + ",\"candyUsed\":0}");
		return withData(named(base, name), t);
	}

	private static ItemStack book(String enchant, int level, String name) {
		CompoundTag t = new CompoundTag();
		t.putString("id", "ENCHANTED_BOOK");
		CompoundTag e = new CompoundTag();
		e.putInt(enchant, level);
		t.put("enchantments", e);
		return withData(named(Items.ENCHANTED_BOOK, name), t);
	}
}
