package dev.lowball.helper.market;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.lowball.helper.util.Fmt;

/** Hits the real APIs. Run with: ./gradlew test -Dlowball.live=true */
@EnabledIfSystemProperty(named = "lowball.live", matches = "true")
class LiveApiTest {
	@Test
	void fullAuctionScan() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		long t0 = System.currentTimeMillis();
		AuctionScanner.Result r = new AuctionScanner(pool).scan(0);
		pool.shutdown();
		assertNotNull(r);
		System.out.printf("Scanned %d auctions into %d keys in %d ms%n", r.auctions(), r.stats().size(), System.currentTimeMillis() - t0);
		assertTrue(r.stats().size() > 1000);
		for (String key : new String[]{"HYPERION", "TERMINATOR", "ASPECT_OF_THE_END", "JUJU_SHORTBOW", "ENDER_DRAGON;4@100", "GOLDEN_DRAGON;4@200", "ENDER_DRAGON;4"}) {
			AuctionStats s = r.stats().get(key);
			if (s == null) {
				System.out.println(key + ": none listed");
				continue;
			}
			StringBuilder sb = new StringBuilder();
			for (double d : s.cheapest()) {
				sb.append(Fmt.coins(d)).append(' ');
			}
			System.out.printf("%-22s lbin %-7s clean %-7s bins %4d bids %3d cheapest [%s]%n", key, Fmt.coins(s.lowest()), Fmt.coins(s.cleanLowest()), s.binCount(), s.auctionCount(), sb.toString().trim());
		}
	}

	@Test
	void bazaarAndItems() throws Exception {
		Map<String, BazaarProduct> bz = BazaarFetcher.fetch();
		assertTrue(bz.size() > 1000);
		for (String id : new String[]{"RECOMBOBULATOR_3000", "HOT_POTATO_BOOK", "FUMING_POTATO_BOOK", "ENCHANTMENT_ULTIMATE_WISE_5", "PERFECT_SAPPHIRE_GEM", "ESSENCE_WITHER", "FIRST_MASTER_STAR"}) {
			BazaarProduct p = bz.get(id);
			System.out.printf("%-28s %s%n", id, p == null ? "missing" : "sell " + Fmt.coins(p.instaSell()) + " buy " + Fmt.coins(p.instaBuy()) + " vol/day " + Fmt.volume(p.dailyVolume()));
		}
		ItemRegistry reg = ItemRegistry.download(java.nio.file.Files.createTempFile("items", ".json"));
		assertTrue(reg.size() > 3000);
		System.out.println("Hyperion stars: " + reg.get("HYPERION").upgradeCosts().size() + ", gem slots: " + reg.get("HYPERION").gemSlots());
	}

	@Test
	void salesTrackerCoflnetAndNeu() throws Exception {
		dev.lowball.helper.LowballHelper.setDataDir(java.nio.file.Files.createTempDirectory("lowball-live"));
		SalesTracker t = new SalesTracker();
		t.poll();
		CoflnetClient c = new CoflnetClient(() -> {
		});
		CoflnetClient.Stats plain = null, clean = null;
		CoflnetClient.History crystal = null;
		for (int i = 0; i < 100 && (plain == null || clean == null || crystal == null); i++) {
			plain = c.get("HYPERION", false);
			clean = c.get("HYPERION", true);
			crystal = c.history("SUPERIOR_DRAGON_CHESTPLATE", "ExoticColor=Crystal");
			Thread.sleep(200);
		}
		assertNotNull(plain);
		assertNotNull(clean);
		assertNotNull(crystal);
		System.out.println("Coflnet HYPERION median " + Fmt.coins(plain.median()) + " (" + plain.perDay() + "/day), clean median "
				+ Fmt.coins(clean.median()) + " (" + clean.perDay() + "/day)");
		System.out.println("Crystal superior chestplates 30d: " + crystal.sales() + " sold, median " + Fmt.coins(crystal.median()));

		NeuRepo neu = new NeuRepo(() -> {
		});
		java.util.Optional<NeuRepo.Recipe> r = null;
		NeuRepo.ReforgeStone fabled = null;
		for (int i = 0; i < 100 && (r == null || fabled == null); i++) {
			r = neu.recipe("TERMINATOR");
			fabled = neu.stone("fabled");
			Thread.sleep(200);
		}
		assertNotNull(r);
		assertTrue(r.isPresent());
		assertNotNull(fabled);
		System.out.println("Terminator recipe: " + r.get().inputs() + "; fabled stone " + fabled.id() + " " + fabled.costs());
	}
}
