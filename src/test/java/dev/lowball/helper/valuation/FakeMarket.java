package dev.lowball.helper.valuation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.NeuRepo;
import dev.lowball.helper.market.SalesTracker;

/** In-memory {@link MarketView} for tests. */
final class FakeMarket implements MarketView {
	final Map<String, Double> prices = new HashMap<>();
	final Map<String, AuctionStats> auctions = new HashMap<>();
	final Map<String, BazaarProduct> bazaar = new HashMap<>();
	final Map<String, CoflnetClient.Stats> cofl = new HashMap<>();
	final Map<String, CoflnetClient.History> history = new HashMap<>();
	final Map<String, Optional<NeuRepo.Recipe>> recipes = new HashMap<>();
	final Map<String, NeuRepo.ReforgeStone> stones = new HashMap<>();
	ItemRegistry registry = ItemRegistry.empty();

	public @Nullable BazaarProduct bazaar(String id) {
		return bazaar.get(id);
	}

	public @Nullable AuctionStats auction(String key) {
		return auctions.get(key);
	}

	public double bazaarPrice(String id) {
		BazaarProduct p = bazaar.get(id);
		return p == null ? Double.NaN : p.sellOfferPrice();
	}

	public double componentPrice(String id) {
		return prices.getOrDefault(id, Double.NaN);
	}

	public @Nullable String componentSource(String id) {
		return prices.containsKey(id) ? "BZ" : null;
	}

	public CoflnetClient.@Nullable Stats coflnet(String key, boolean clean) {
		return cofl.get(key + (clean ? "|clean" : ""));
	}

	public CoflnetClient.@Nullable History history(String id, String filters, String period) {
		return history.get(id + "?" + filters);
	}

	public SalesTracker.@Nullable Volume localVolume(String key) {
		return null;
	}

	public ItemRegistry registry() {
		return registry;
	}

	public @Nullable Optional<NeuRepo.Recipe> recipe(String id) {
		return recipes.getOrDefault(id, Optional.empty());
	}

	public NeuRepo.@Nullable ReforgeStone stone(String modifier) {
		return stones.get(modifier);
	}

	public boolean auctionsLoaded() {
		return true;
	}
}
