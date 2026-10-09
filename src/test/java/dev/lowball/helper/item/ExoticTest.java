package dev.lowball.helper.item;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class ExoticTest {
	static SkyblockItem piece(String id, int color, String dye) {
		CompoundTag t = new CompoundTag();
		t.putString("id", id);
		if (dye != null) {
			t.putString("dye_item", dye);
		}
		return SkyblockItem.of(t, id, 1, color);
	}

	@Test
	void classifies() {
		int def = 0xF2DF11;
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", def, null), def));
		assertEquals(Exotic.Type.EXOTIC, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0x123456, null), def));
		assertEquals(Exotic.Type.CRYSTAL, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0x1F0030, null), def));
		assertEquals(Exotic.Type.FAIRY, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0xFF99CC, null), def));
		assertEquals(Exotic.Type.SPOOK, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0x000000, null), def));
		// dyes and unknown defaults are never exotic
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0x123456, "DYE_NECRON"), def));
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("SUPERIOR_DRAGON_CHESTPLATE", 0x123456, null), -1));
		// crystal on crystal armor and fairy on fairy armor are normal
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("CRYSTAL_CHESTPLATE", 0x1F0030, null), 0xFFFFFF));
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("FAIRY_CHESTPLATE", 0xFF99CC, null), 0xFFFFFF));
		assertEquals(Exotic.Type.OG_FAIRY, Exotic.classify(piece("FAIRY_CHESTPLATE", 0xFF66FF, null), 0xFFFFFF));
		assertEquals(Exotic.Type.NONE, Exotic.classify(piece("VELVET_TOP_HAT", 0x123456, null), 0x000001));
	}

	@Test
	void parsesColors() {
		assertEquals(0xF2DF11, Exotic.parseRgb("242,223,17"));
		assertEquals(-1, Exotic.parseRgb("nope"));
		assertEquals("31:0:48", Exotic.coflnetColor(0x1F0030));
	}
}
