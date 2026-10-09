package dev.lowball.helper.valuation;

import java.io.Reader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import dev.lowball.helper.LowballHelper;
import dev.lowball.helper.config.LowballConfig;
import dev.lowball.helper.item.ItemBytes;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionScanner;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarFetcher;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.NeuRepo;
import dev.lowball.helper.market.SalesTracker;
import dev.lowball.helper.util.Fmt;
import dev.lowball.helper.util.Http;

/**
 * Accuracy check against reality: values auctions that just sold and compares the estimate to the price they sold for.
 * Run: ./gradlew test --tests '*LiveAccuracyTest*' -Dlowball.live=true
 */
@EnabledIfSystemProperty(named = "lowball.live", matches = "true")
class LiveAccuracyTest {
	record Sale(SkyblockItem item, double price) {
	}

	@Test
	void estimatesMatchRealSales() throws Exception {
		LowballHelper.setDataDir(Files.createTempDirectory("lowball-acc"));
		ExecutorService pool = Executors.newFixedThreadPool(4);
		ItemRegistry registry = ItemRegistry.download(Files.createTempFile("items", ".json"));
		Map<String, BazaarProduct> bazaar = BazaarFetcher.fetch();
		Map<String, AuctionStats> auctions = new AuctionScanner(pool, registry::defaultColor).scan(0).stats();
		CoflnetClient cofl = new CoflnetClient(() -> {
		});
		NeuRepo neu = new NeuRepo(() -> {
		});
		LowballConfig cfg = new LowballConfig();
		cfg.sanitize();

		MarketView view = new MarketView() {
			public @Nullable BazaarProduct bazaar(String id) {
				return bazaar.get(id);
			}

			public @Nullable AuctionStats auction(String key) {
				return auctions.get(key);
			}

			public double bazaarPrice(String id) {
				BazaarProduct p = bazaar.get(id);
				if (p == null) {
					return Double.NaN;
				}
				double v = cfg.bazaarMode == dev.lowball.helper.config.BazaarMode.INSTA_SELL ? p.safeInstaSell() : p.sellOfferPrice();
				return v > 0 ? v : Double.NaN;
			}

			public double componentPrice(String id) {
				double bz = bazaarPrice(id);
				if (!Double.isNaN(bz)) {
					return bz;
				}
				AuctionStats a = auctions.get(id);
				return a == null ? Double.NaN : !Double.isNaN(a.cleanLowest()) ? a.cleanLowest() : a.lowest();
			}

			public @Nullable String componentSource(String id) {
				return bazaar.containsKey(id) ? "BZ" : auctions.containsKey(id) ? "AH" : null;
			}

			public CoflnetClient.@Nullable Stats coflnet(String key, boolean clean) {
				return cofl.get(key, clean);
			}

			public CoflnetClient.@Nullable History history(String id, String filters, String period) {
				return cofl.history(id, filters, period);
			}

			public SalesTracker.@Nullable Volume localVolume(String key) {
				return null;
			}

			public ItemRegistry registry() {
				return registry;
			}

			public @Nullable Optional<NeuRepo.Recipe> recipe(String id) {
				return neu.recipe(id);
			}

			public NeuRepo.@Nullable ReforgeStone stone(String modifier) {
				return neu.stone(modifier);
			}

			public boolean auctionsLoaded() {
				return true;
			}
		};

		// ground truth: BIN auctions that just sold
		List<Sale> sales = new ArrayList<>();
		Map<String, Integer> perKey = new HashMap<>();
		int polls = Integer.getInteger("lowball.polls", 1);
		boolean upgradedOnly = Boolean.getBoolean("lowball.upgraded");
		java.util.Set<String> seen = new java.util.HashSet<>();
		List<JsonElement> ended = new ArrayList<>();
		for (int poll = 0; poll < polls; poll++) {
			if (poll > 0) {
				Thread.sleep(65_000);
			}
			try (Reader r = Http.reader("https://api.hypixel.net/v2/skyblock/auctions_ended")) {
				for (JsonElement el : JsonParser.parseReader(r).getAsJsonObject().getAsJsonArray("auctions")) {
					if (seen.add(el.getAsJsonObject().get("auction_id").getAsString())) {
						ended.add(el);
					}
				}
			}
		}
		for (JsonElement el : ended) {
			JsonObject a = el.getAsJsonObject();
			if (!a.get("bin").getAsBoolean()) {
				continue;
			}
			double price = a.get("price").getAsDouble();
			SkyblockItem item = ItemBytes.decodeFirst(a.get("item_bytes").getAsString(), "");
			if (item == null || bazaar.containsKey(item.key) || price / item.count < 1_000_000) {
				continue;
			}
			if (upgradedOnly && item.isClean() && item.enchants.isEmpty()) {
				continue;
			}
			if (perKey.merge(item.key, 1, Integer::sum) > 2) {
				continue;
			}
			sales.add(new Sale(item, price));
		}
		System.out.println("Evaluating " + sales.size() + " real sales");

		List<Double> ratios = new ArrayList<>();
		List<String> rows = new ArrayList<>();
		for (Sale s : sales) {
			Valuation v = null;
			// let lookups (Coflnet, recipes) arrive
			for (int i = 0; i < 40; i++) {
				v = Valuator.compute(s.item(), cfg, view);
				Thread.sleep(250);
				if (i > 6 && !cofl.pending()) {
					v = Valuator.compute(s.item(), cfg, view);
					break;
				}
			}
			if (v == null || !v.known()) {
				rows.add(String.format("  %-34s sold %8s  est    ?", s.item().key, Fmt.coins(s.price())));
				continue;
			}
			double ratio = v.totalValue() / s.price();
			ratios.add(ratio);
			rows.add(String.format("  %-34s sold %8s  est %8s  x%.2f  base %s (%s) +up %s%s", trim(s.item().key), Fmt.coins(s.price()),
					Fmt.coins(v.totalValue()), ratio, Fmt.coins(v.unitBase()), v.baseSource(), Fmt.coins(v.upgradesCredited()),
					v.worthNote().isEmpty() ? "" : " [" + v.worthNote() + "]"));
		}
		pool.shutdown();
		rows.forEach(System.out::println);
		ratios.sort(Double::compare);
		int n = ratios.size();
		if (n == 0) {
			return;
		}
		double mean = ratios.stream().mapToDouble(d -> d).average().orElse(Double.NaN);
		long over20 = ratios.stream().filter(r -> r > 1.2).count();
		long under20 = ratios.stream().filter(r -> r < 0.8).count();
		System.out.printf("RESULT n=%d median x%.3f mean x%.3f p25 x%.2f p75 x%.2f  >20%% over: %d  >20%% under: %d%n",
				n, ratios.get(n / 2), mean, ratios.get(n / 4), ratios.get(3 * n / 4), over20, under20);
	}

	private static String trim(String s) {
		return s.length() > 34 ? s.substring(0, 34) : s;
	}
}
