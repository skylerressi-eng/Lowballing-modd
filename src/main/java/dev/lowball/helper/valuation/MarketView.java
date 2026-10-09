package dev.lowball.helper.valuation;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;
import dev.lowball.helper.market.ItemRegistry;
import dev.lowball.helper.market.NeuRepo;
import dev.lowball.helper.market.SalesTracker;

/** Everything the valuator reads, so it can run against live data or test fixtures. */
public interface MarketView {
	@Nullable BazaarProduct bazaar(String id);

	@Nullable AuctionStats auction(String key);

	/** Bazaar price in the configured mode, NaN if not a bazaar product. */
	double bazaarPrice(String id);

	/** Bazaar or cheapest BIN price of a component, NaN if unknown. */
	double componentPrice(String id);

	@Nullable String componentSource(String id);

	CoflnetClient.@Nullable Stats coflnet(String key, boolean clean);

	/** Sale history of an item id with Coflnet filters over "week" or "month". */
	CoflnetClient.@Nullable History history(String id, String filters, String period);

	SalesTracker.@Nullable Volume localVolume(String key);

	ItemRegistry registry();

	@Nullable Optional<NeuRepo.Recipe> recipe(String id);

	NeuRepo.@Nullable ReforgeStone stone(String modifier);

	boolean auctionsLoaded();
}
