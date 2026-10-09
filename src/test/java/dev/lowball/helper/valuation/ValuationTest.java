package dev.lowball.helper.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.config.Preset;
import dev.lowball.helper.config.RoundMode;
import dev.lowball.helper.config.ValueMode;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;

class ValuationTest {
	static final Map<String, Double> PRICES = Map.of(
			"RECOMBOBULATOR_3000", 6_000_000d,
			"HOT_POTATO_BOOK", 80_000d,
			"FUMING_POTATO_BOOK", 1_000_000d,
			"ENCHANTMENT_ULTIMATE_WISE_5", 800_000d,
			"PERFECT_SAPPHIRE_GEM", 16_000_000d,
			"FLAWLESS_JASPER_GEM", 2_000_000d,
			"ESSENCE_WITHER", 2_000d,
			"FIRST_MASTER_STAR", 9_000_000d);

	static final ItemRegistry REGISTRY = new ItemRegistry(Map.of("HYPERION", new ItemRegistry.Info("HYPERION", "Hyperion", "LEGENDARY", 0,
			List.of(List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 150)), List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 300)),
					List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 500)), List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 900)),
					List.of(new ItemRegistry.Cost("ESSENCE_WITHER", 1500))),
			List.of(new ItemRegistry.GemSlot("SAPPHIRE", List.of(new ItemRegistry.Cost(null, 250_000), new ItemRegistry.Cost("FLAWLESS_SAPPHIRE_GEM", 4))),
					new ItemRegistry.GemSlot("COMBAT", List.of(new ItemRegistry.Cost(null, 250_000), new ItemRegistry.Cost("FLAWLESS_JASPER_GEM", 1)))),
			true)));

	@BeforeAll
	static void isolateConfig() throws Exception {
		LowballHelper.setDataDir(Files.createTempDirectory("lowball-test"));
	}

	static double price(String id) {
		return PRICES.getOrDefault(id, Double.NaN);
	}

	static LowballConfig cfg() {
		LowballConfig c = new LowballConfig();
		c.preset = Preset.STANDARD;
		c.roundMode = RoundMode.NONE;
		return c;
	}

	static SkyblockItem upgradedHyperion() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "HYPERION");
		t.putInt("rarity_upgrades", 1);
		t.putInt("hot_potato_count", 15);
		t.putInt("upgrade_level", 6);
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
		List<Upgrade> ups = UpgradeValuer.value(upgradedHyperion(), ValuationTest::price, REGISTRY);
		double sum = ups.stream().mapToDouble(Upgrade::total).sum();
		double expected = 6_000_000 // recomb
				+ 10 * 80_000 + 5 * 1_000_000 // potato books
				+ 800_000 // ult wise
				+ (150 + 300 + 500 + 900 + 1500) * 2_000 // 5 stars
				+ 9_000_000 // first master star
				+ 16_000_000 + 2_000_000 // gems
				+ 2_000_000 + 250_000; // combat slot unlock
		assertEquals(expected, sum, 1e-6);
	}

	@Test
	void cleanLbinPlusCreditedUpgrades() {
		SkyblockItem item = upgradedHyperion();
		AuctionStats ah = new AuctionStats("HYPERION", "Hyperion", 700e6, 650e6, new double[]{700e6, 720e6}, 40, 1);
		LowballConfig c = cfg();
		c.upgradeCredit = 50;
		Valuation v = Valuator.compute(item, c, null, ah, null, null, REGISTRY, ValuationTest::price, Double.NaN);
		assertEquals("Clean LBIN", v.baseSource());
		assertEquals(650e6 + v.upgradesRaw() * 0.5, v.unitValue(), 1e-3);
	}

	@Test
	void smartModeDistrustsInflatedLbin() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "RARE_THING");
		SkyblockItem item = SkyblockItem.of(t, "Rare Thing", 1);
		AuctionStats ah = new AuctionStats("RARE_THING", "Rare Thing", 200e6, 200e6, new double[]{200e6}, 1, 0);
		CoflnetClient.Stats cofl = new CoflnetClient.Stats(100e6, 4, 90e6, 130e6, System.currentTimeMillis(), true);
		Valuation v = Valuator.compute(item, cfg(), null, ah, cofl, null, REGISTRY, ValuationTest::price, Double.NaN);
		assertEquals(100e6, v.unitBase());
		assertTrue(v.baseSource().contains("inflated"));

		LowballConfig lbin = cfg();
		lbin.valueMode = ValueMode.LBIN;
		assertEquals(200e6, Valuator.compute(item, lbin, null, ah, cofl, null, REGISTRY, ValuationTest::price, Double.NaN).unitBase());
	}

	@Test
	void offerAdjustsForVolumeAndSupply() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "THING");
		SkyblockItem item = SkyblockItem.of(t, "Thing", 1);
		LowballConfig c = cfg();

		// fast seller with thin supply: 75 + 5
		AuctionStats thin = new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 5, 0);
		CoflnetClient.Stats fast = new CoflnetClient.Stats(10e6, 100, 0, 0, 0, true);
		Offer o = OfferCalculator.offer(Valuator.compute(item, c, null, thin, fast, null, REGISTRY, ValuationTest::price, Double.NaN), c);
		assertEquals(0.80, o.percent(), 1e-9);
		assertEquals(8e6, o.totalOffer(), 1e-6);
		assertEquals(10e6 * 0.97 - 8e6, o.profit(), 1e-6);

		// slow seller with a flooded AH: 75 - 10 (volume 2/day) - 10 (40 days of supply)
		AuctionStats flooded = new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 80, 0);
		CoflnetClient.Stats slow = new CoflnetClient.Stats(10e6, 2, 0, 0, 0, true);
		Offer o2 = OfferCalculator.offer(Valuator.compute(item, c, null, flooded, slow, null, REGISTRY, ValuationTest::price, Double.NaN), c);
		assertEquals(0.55, o2.percent(), 1e-9);
	}

	@Test
	void minProfitCapsOffer() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "THING");
		SkyblockItem item = SkyblockItem.of(t, "Thing", 1);
		LowballConfig c = cfg();
		c.preset = Preset.GENEROUS;
		c.volumeAdjust = false;
		c.minProfit = 1_000_000;
		AuctionStats ah = new AuctionStats("THING", "Thing", 10e6, 10e6, new double[]{10e6}, 5, 0);
		Offer o = OfferCalculator.offer(Valuator.compute(item, c, null, ah, null, null, REGISTRY, ValuationTest::price, Double.NaN), c);
		assertEquals(10e6 * 0.97 - 1e6, o.totalOffer(), 1e-6); // 10M is in the 2% listing bracket
		assertEquals(1e6, o.profit(), 1e-6);
	}

	@Test
	void roundingNeverRoundsUp() {
		CompoundTag t = new CompoundTag();
		t.putString("id", "THING");
		SkyblockItem item = SkyblockItem.of(t, "Thing", 1);
		LowballConfig c = cfg();
		c.volumeAdjust = false;
		c.roundMode = RoundMode.SIG2;
		AuctionStats ah = new AuctionStats("THING", "Thing", 12_345_678, 12_345_678, new double[]{12_345_678}, 5, 0);
		Offer o = OfferCalculator.offer(Valuator.compute(item, c, null, ah, null, null, REGISTRY, ValuationTest::price, Double.NaN), c);
		assertEquals(9_200_000, o.totalOffer(), 1e-6); // 75% = 9,259,258 -> 9.2M
	}
}
