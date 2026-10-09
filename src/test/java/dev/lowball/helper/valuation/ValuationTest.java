package dev.lowball.helper.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.config.Preset;
import dev.lowball.helper.config.RoundMode;
import dev.lowball.helper.config.UpgradeMode;
import dev.lowball.helper.config.ValueMode;
import dev.lowball.helper.item.Exotic;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionScanner;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.NeuRepo;

class ValuationTest {
	static final ItemRegistry REGISTRY = new ItemRegistry(Map.of(
			"HYPERION", new ItemRegistry.Info("HYPERION", "Hyperion", "LEGENDARY", "SWORD", 0,
					List.of(List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 150)), List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 300)),
							List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 500)), List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 900)),
							List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 1500))),
					List.of(new ItemRegistry.GemSlot("SAPPHIRE", List.of(new ItemRegistry.Cost(null, 250_000), new ItemRegistry.Cost("FLAWLESS_SAPPHIRE_GEM", 4))),
							new ItemRegistry.GemSlot("COMBAT", List.of(new ItemRegistry.Cost(null, 250_000), new ItemRegistry.Cost("FLAWLESS_JASPER_GEM", 1)))),
					true, -1),
			"SUPERIOR_DRAGON_CHESTPLATE", new ItemRegistry.Info("SUPERIOR_DRAGON_CHESTPLATE", "Superior Dragon Chestplate", "LEGENDARY", "CHESTPLATE",
					0, List.of(), List.of(), false, 0xF2DF11),
			"THING", new ItemRegistry.Info("THING", "Thing", "RARE", "SWORD", 1000, List.of(), List.of(), false, -1)));

	@BeforeAll
	static void isolateConfig() throws Exception {
		LowballHelper.setDataDir(Files.createTempDirectory("lowball-test"));
	}

	static FakeMarket market() {
		FakeMarket m = new FakeMarket();
		m.registry = REGISTRY;
		m.prices.putAll(Map.of(
				"RECOMBOBULATOR_3000", 6_000_000d,
				"HOT_POTATO_BOOK", 80_000d,
				"FUMING_POTATO_BOOK", 1_000_000d,
				"ENCHANTMENT_ULTIMATE_WISE_5", 800_000d,
				"PERFECT_SAPPHIRE_GEM", 16_000_000d,
				"FLAWLESS_JASPER_GEM", 2_000_000d,
				"ESSENCE_WITHER", 2_000d,
				"FIRST_MASTER_STAR", 9_000_000d,
				"DRAGON_CLAW", 3_000_000d));
		return m;
	}

	static LowballConfig cfg() {
		LowballConfig c = new LowballConfig();
		c.preset = Preset.STANDARD;
		c.roundMode = RoundMode.NONE;
		c.sanitize();
		return c;
	}

	static SkyblockItem thing() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "THING");
		return SkyblockItem.of(t, "Thing", 1);
	}

	static SkyblockItem upgradedHyperion() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "HYPERION");
		t.putInt("rarity_upgrades", 1);
		t.putInt("hot_potato_count", 15);
		t.putInt("upgrade_level", 6);
		t.putString("modifier", "fabled");
		CompoundTag ench = new CompoundTag();
		ench.putInt("ultimate_wise", 5);
		t.put("enchantments", ench);
		CompoundTag gems = new CompoundTag();
		gems.putString("SAPPHIRE_0", "PERFECT");
		CompoundTag combat = new CompoundTag();
		combat.putString("quality", "FLAWLESS");
		gems.put("COMBAT_0", combat);
		gems.putString("COMBAT_0_gem", "JASPER");
		ListTag unlocked = new ListTag();
		unlocked.add(StringTag.valueOf("COMBAT_0"));
		gems.put("unlocked_slots", unlocked);
		t.put("gems", gems);
		return SkyblockItem.of(t, "Hyperion ✪✪✪✪✪➊", 1);
	}

	@Test
	void upgradesArePricedFromComponents() {
		FakeMarket m = market();
		m.stones.put("fabled", new NeuRepo.ReforgeStone("DRAGON_CLAW", "Fabled", Map.of("MYTHIC", 1_000_000d)));
		List<Upgrade> ups = UpgradeValuer.value(upgradedHyperion(), m::componentPrice, REGISTRY, m::stone);
		double sum = ups.stream().mapToDouble(Upgrade::total).sum();
		double expected = 6_000_000 // recomb
				+ 10 * 80_000 + 5 * 1_000_000 // potato books
				+ 800_000 // ult wise
				+ (150 + 300 + 500 + 900 + 1500) * 2_000 // 5 stars
				+ 9_000_000 // first master star
				+ 16_000_000 + 2_000_000 // gems
				+ 2_000_000 + 250_000 // combat slot unlock
				+ 3_000_000 + 1_000_000; // fabled: dragon claw + mythic apply cost (recombed legendary)
		assertEquals(expected, sum, 1e-6);
		assertTrue(ups.stream().anyMatch(u -> u.category() == UpgradeCategory.GEMSTONES));
		assertTrue(ups.stream().anyMatch(u -> u.category() == UpgradeCategory.MASTER_STARS));
	}

	@Test
	void cleanLbinPlusCreditedUpgradesPerCategory() {
		FakeMarket m = market();
		m.auctions.put("HYPERION", new AuctionStats("HYPERION", "Hyperion", 700e6, 650e6, new double[]{700e6, 720e6}, 40, 1));
		LowballConfig c = cfg();
		c.upgradeMode = UpgradeMode.BALANCED;
		Valuation v = Valuator.compute(upgradedHyperion(), c, m);
		assertEquals("Clean LBIN", v.baseSource());
		double credited = 0;
		for (Valuation.Credited u : v.upgrades()) {
			assertEquals(u.upgrade().category().defaultCredit / 100.0, u.credit(), 1e-9);
			credited += u.credited();
		}
		assertEquals(650e6 + credited, v.unitValue(), 1e-3);

		c.upgradeMode = UpgradeMode.FULL;
		assertEquals(650e6 + v.upgradesRaw(), Valuator.compute(upgradedHyperion(), c, m).unitValue(), 1e-3);
		c.upgradeMode = UpgradeMode.NONE;
		assertEquals(650e6, Valuator.compute(upgradedHyperion(), c, m).unitValue(), 1e-3);
	}

	@Test
	void smartModeUsesCleanMedianWhenLbinInflated() {
		FakeMarket m = market();
		m.auctions.put("THING", new AuctionStats("THING", "Thing", 200e6, 200e6, new double[]{200e6}, 1, 0));
		m.cofl.put("THING", new CoflnetClient.Stats(150e6, 10, 0, 0, 0, true));
		m.cofl.put("THING|clean", new CoflnetClient.Stats(100e6, 4, 0, 0, 0, true));
		Valuation v = Valuator.compute(thing(), cfg(), m);
		assertEquals(100e6, v.unitBase());
		assertTrue(v.baseSource().contains("inflated"), v.baseSource());
		assertTrue(v.candidates().stream().filter(Valuation.Candidate::chosen).count() == 1);

		LowballConfig lbin = cfg();
		lbin.valueMode = ValueMode.LBIN;
		assertEquals(200e6, Valuator.compute(thing(), lbin, m).unitBase());
	}

	@Test
	void craftCostCapsBaseAndFillsGaps() {
		FakeMarket m = market();
		m.auctions.put("THING", new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 5, 0));
		m.prices.put("PART", 1e6);
		m.prices.put("RARE_PART", Double.NaN);
		m.recipes.put("THING", Optional.of(new NeuRepo.Recipe("crafting", List.of(new NeuRepo.Ingredient("PART", 4), new NeuRepo.Ingredient("SUB", 2)), 0, 1)));
		m.recipes.put("SUB", Optional.of(new NeuRepo.Recipe("forge", List.of(new NeuRepo.Ingredient("PART", 1)), 500_000, 1)));
		LowballConfig cap = cfg();
		cap.craftCap = true;
		Valuation v = Valuator.compute(thing(), cap, m);
		assertNotNull(v.craft());
		assertTrue(v.craft().complete());
		// 4 × 1M + 2 × (1M + 500k coins) = 7M; capped at craft + 20% = 8.4M, below the 10M LBIN
		assertEquals(7e6, v.craft().total(), 1e-6);
		assertEquals(8.4e6, v.unitBase(), 1e-6);
		assertTrue(v.baseSource().startsWith("Craft cost"));

		LowballConfig noCap = cfg();
		noCap.craftCap = false;
		assertEquals(10e6, Valuator.compute(thing(), noCap, m).unitBase());
	}

	@Test
	void exoticArmorPricedFromSameColorSales() {
		FakeMarket m = market();
		CompoundTag t = new CompoundTag();
		t.putString("id", "SUPERIOR_DRAGON_CHESTPLATE");
		SkyblockItem item = SkyblockItem.of(t, "Superior Dragon Chestplate", 1, 0x1F0030);
		assertEquals(Exotic.Type.CRYSTAL, Exotic.classify(item, 0xF2DF11));
		m.auctions.put("SUPERIOR_DRAGON_CHESTPLATE", new AuctionStats("SUPERIOR_DRAGON_CHESTPLATE", "x", 5e6, 5e6, new double[]{5e6}, 50, 0));
		m.history.put("SUPERIOR_DRAGON_CHESTPLATE?Color=31:0:48", new CoflnetClient.History(3, 150e6, 5e6, 170e6, 0, 0, true));
		m.auctions.put(AuctionScanner.exoticHexKey(item.id, item.color), new AuctionStats("k", "x", 200e6, 200e6, new double[]{200e6}, 1, 0));
		Valuation v = Valuator.compute(item, cfg(), m);
		assertNotNull(v.exotic());
		assertEquals(150e6, v.unitBase());
		assertTrue(v.baseSource().startsWith("Exotic"));
		assertEquals(0.1, v.dailyVolume(), 1e-9);
		assertEquals(1, v.listed());
	}

	@Test
	void offerAdjustsForVolumeAndSupply() {
		FakeMarket m = market();
		LowballConfig c = cfg();
		m.auctions.put("THING", new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 5, 0));
		m.cofl.put("THING", new CoflnetClient.Stats(10e6, 100, 0, 0, 0, true));
		Offer o = OfferCalculator.offer(Valuator.compute(thing(), c, m), c);
		assertEquals(0.80, o.percent(), 1e-9);
		assertEquals(8e6, o.totalOffer(), 1e-6);
		assertEquals(10e6 * 0.97 - 8e6, o.profit(), 1e-6);

		m.auctions.put("THING", new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 80, 0));
		m.cofl.put("THING", new CoflnetClient.Stats(10e6, 2, 0, 0, 0, true));
		assertEquals(0.55, OfferCalculator.offer(Valuator.compute(thing(), c, m), c).percent(), 1e-9);
	}

	@Test
	void minProfitCapsOffer() {
		FakeMarket m = market();
		LowballConfig c = cfg();
		c.preset = Preset.GENEROUS;
		c.volumeAdjust = false;
		c.minProfit = 1_000_000;
		m.auctions.put("THING", new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 5, 0));
		Offer o = OfferCalculator.offer(Valuator.compute(thing(), c, m), c);
		assertEquals(10e6 * 0.97 - 1e6, o.totalOffer(), 1e-6); // 10M is in the 2% listing bracket
		assertEquals(1e6, o.profit(), 1e-6);
	}

	@Test
	void roundingNeverRoundsUp() {
		FakeMarket m = market();
		LowballConfig c = cfg();
		c.volumeAdjust = false;
		c.roundMode = RoundMode.SIG2;
		m.auctions.put("THING", new AuctionStats("THING", "Thing", 12_345_678, 12_345_678, new double[]{12_345_678}, 5, 0));
		assertEquals(9_200_000, OfferCalculator.offer(Valuator.compute(thing(), c, m), c).totalOffer(), 1e-6);
	}

	@Test
	void npcFallbackWhenNoMarket() {
		Valuation v = Valuator.compute(thing(), cfg(), market());
		assertEquals(1000, v.unitBase());
		assertEquals("NPC sell price", v.baseSource());
	}
}
