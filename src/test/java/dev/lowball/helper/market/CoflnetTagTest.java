package dev.lowball.helper.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CoflnetTagTest {
	@Test
	void mapsKeys() {
		assertEquals("HYPERION", CoflnetClient.tag("HYPERION"));
		assertEquals("PET_ENDER_DRAGON?Rarity=LEGENDARY&PetLevel=100-199", CoflnetClient.tag("ENDER_DRAGON;4@100"));
		assertEquals("PET_GOLDEN_DRAGON?Rarity=LEGENDARY&PetLevel=200", CoflnetClient.tag("GOLDEN_DRAGON;4@200"));
		assertEquals("PET_BAL?Rarity=EPIC&PetLevel=1-99", CoflnetClient.tag("BAL;3@LOW"));
		assertEquals("PET_BAL?Rarity=EPIC", CoflnetClient.tag("BAL;3"));
		assertNull(CoflnetClient.tag("ENCHANTMENT_SHARPNESS_6"));
		assertNull(CoflnetClient.tag("SPIRIT_RUNE;3"));
	}
}
