package dev.lowball.helper.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class SkyblockItemTest {
	static CompoundTag tag(String id) {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		return t;
	}

	@Test
	void plainItemUsesId() {
		SkyblockItem i = SkyblockItem.of(tag("HYPERION"), "§dHeroic Hyperion §6✪✪✪✪✪", 1);
		assertEquals("HYPERION", i.key);
		assertEquals("Heroic Hyperion", i.name);
		assertTrue(i.isClean());
	}

	@Test
	void noIdIsNotSkyblock() {
		assertNull(SkyblockItem.of(new CompoundTag(), "Stone", 1));
		assertNull(SkyblockItem.of(null, "Stone", 1));
	}

	@Test
	void legacyWrapperIsUnwrapped() {
		CompoundTag outer = new CompoundTag();
		outer.put("ExtraAttributes", tag("TERMINATOR"));
		assertEquals("TERMINATOR", SkyblockItem.of(outer, "Terminator", 1).key);
	}

	@Test
	void upgradesMakeItemDirty() {
		CompoundTag t = tag("HYPERION");
		t.putInt("rarity_upgrades", 1);
		t.putInt("hot_potato_count", 15);
		t.putInt("upgrade_level", 5);
		SkyblockItem i = SkyblockItem.of(t, "Hyperion", 1);
		assertFalse(i.isClean());
		assertEquals(1, i.recombs);
		assertEquals(15, i.potatoBooks);
		assertEquals(5, i.stars);
	}

	@Test
	void petsKeyByTypeTierAndLevelBucket() {
		CompoundTag t = tag("PET");
		t.putString("petInfo", "{\"type\":\"ENDER_DRAGON\",\"active\":false,\"exp\":2.5E7,\"tier\":\"LEGENDARY\",\"heldItem\":\"PET_ITEM_SPOOKY_CUPCAKE\",\"candyUsed\":0}");
		SkyblockItem i = SkyblockItem.of(t, "§7[Lvl 100] §6Ender Dragon", 1);
		assertTrue(i.pet);
		assertEquals("ENDER_DRAGON;4", i.baseKey);
		assertEquals("ENDER_DRAGON;4@100", i.key);
		assertEquals(100, i.petLevel);
		assertEquals("PET_ITEM_SPOOKY_CUPCAKE", i.petHeldItem);

		SkyblockItem low = SkyblockItem.of(t, "[Lvl 37] Ender Dragon", 1);
		assertEquals("ENDER_DRAGON;4@LOW", low.key);
		SkyblockItem gdrag = SkyblockItem.of(t, "[Lvl 200] Golden Dragon", 1);
		assertEquals("ENDER_DRAGON;4@200", gdrag.key);
	}

	@Test
	void tierBoostedPetPricesAtBaseTier() {
		CompoundTag t = tag("PET");
		t.putString("petInfo", "{\"type\":\"BAL\",\"tier\":\"LEGENDARY\",\"heldItem\":\"PET_ITEM_TIER_BOOST\"}");
		assertEquals("BAL;3@100", SkyblockItem.of(t, "[Lvl 100] Bal", 1).key);
	}

	@Test
	void singleEnchantBookKeysToBazaarProduct() {
		CompoundTag t = tag("ENCHANTED_BOOK");
		CompoundTag ench = new CompoundTag();
		ench.putInt("ultimate_wise", 5);
		t.put("enchantments", ench);
		assertEquals("ENCHANTMENT_ULTIMATE_WISE_5", SkyblockItem.of(t, "Enchanted Book", 1).key);

		ench.putInt("sharpness", 6);
		assertEquals("ENCHANTED_BOOK", SkyblockItem.of(t, "Enchanted Book", 1).key);
	}

	@Test
	void runesKeyByNameAndLevel() {
		CompoundTag t = tag("RUNE");
		CompoundTag r = new CompoundTag();
		r.putInt("SPIRIT", 3);
		t.put("runes", r);
		assertEquals("SPIRIT_RUNE;3", SkyblockItem.of(t, "◆ Spirit Rune III", 1).key);
	}

	@Test
	void stripsHypixelIconGlyphs() {
		assertEquals("Ancient Shadow Assassin Boots", SkyblockItem.of(tag("SHADOW_ASSASSIN_BOOTS"), " Ancient Shadow Assassin Boots ✪✪✪", 1).name);
	}
}
