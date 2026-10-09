package dev.lowball.helper.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class NeuRepoTest {
	@Test
	void parsesCraftingGrid() {
		NeuRepo.Recipe r = NeuRepo.parseRecipe("""
				{"internalname":"HYPERION","recipe":{"A1":"GIANT_FRAGMENT_LASER:1","A2":"GIANT_FRAGMENT_LASER:1","A3":"GIANT_FRAGMENT_LASER:1",
				"B1":"GIANT_FRAGMENT_LASER:1","B2":"NECRON_BLADE:1","B3":"GIANT_FRAGMENT_LASER:1","C1":"GIANT_FRAGMENT_LASER:1",
				"C2":"GIANT_FRAGMENT_LASER:1","C3":"GIANT_FRAGMENT_LASER:1"}}""");
		assertNotNull(r);
		assertEquals("crafting", r.type());
		assertEquals(List.of(new NeuRepo.Ingredient("GIANT_FRAGMENT_LASER", 8), new NeuRepo.Ingredient("NECRON_BLADE", 1)), r.inputs());
	}

	@Test
	void parsesForgeWithCoins() {
		NeuRepo.Recipe r = NeuRepo.parseRecipe("""
				{"recipes":[{"type":"forge","inputs":["TITANIUM_DRILL_4:1","SKYBLOCK_COIN:50000000","DIVAN_ALLOY:1"],"count":1}]}""");
		assertNotNull(r);
		assertEquals("forge", r.type());
		assertEquals(50_000_000, r.coins());
		assertEquals(2, r.inputs().size());
	}

	@Test
	void noRecipeIsNull() {
		assertEquals(null, NeuRepo.parseRecipe("{\"internalname\":\"NECRON_HANDLE\"}"));
	}

	@Test
	void parsesReforgeStones() {
		var stones = NeuRepo.parseStones("""
				{"DRAGON_CLAW":{"internalName":"DRAGON_CLAW","reforgeName":"Fabled","reforgeCosts":{"LEGENDARY":800000,"MYTHIC":1600000}}}""");
		assertEquals("DRAGON_CLAW", stones.get("fabled").id());
		assertEquals(1_600_000, stones.get("fabled").costs().get("MYTHIC"));
	}

	@Test
	void weightedMedianOfDailyAverages() {
		assertEquals(150e6, CoflnetClient.weightedMedian(List.of(new double[]{5e6, 1}, new double[]{150e6, 3}, new double[]{170e6, 1})));
	}
}
