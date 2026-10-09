package dev.lowball.helper.valuation;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.item.Exotic;
import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;

/**
 * Everything known about what one stack is worth, including every number that was considered,
 * so the UI can show exactly how the value was calculated.
 *
 * @param unitBase    price of the base item per unit, NaN if unknown
 * @param candidates  every base price that was considered; one is {@code chosen}
 * @param dailyVolume units sold per day, NaN if unknown
 * @param listed      current listings (BINs on AH or sell offers on bazaar), -1 if unknown
 * @param unitValue   base + credited upgrades, per unit
 */
public record Valuation(
		SkyblockItem item,
		boolean bazaar,
		double unitBase,
		String baseSource,
		List<Candidate> candidates,
		@Nullable AuctionStats auction,
		@Nullable BazaarProduct bazaarProduct,
		CoflnetClient.@Nullable Stats coflnet,
		CoflnetClient.@Nullable Stats coflnetClean,
		double median,
		double cleanMedian,
		double dailyVolume,
		String volumeSource,
		int listed,
		List<Credited> upgrades,
		@Nullable CraftCost craft,
		@Nullable ExoticInfo exotic,
		double unitValue,
		List<String> warnings) {

	/** A base price that was considered. */
	public record Candidate(String label, double value, String detail, boolean chosen) {
	}

	/** An upgrade with the share of its cost that counts. */
	public record Credited(Upgrade upgrade, double credit) {
		public double total() {
			return upgrade.total();
		}

		public double credited() {
			return upgrade.total() * credit;
		}
	}

	/**
	 * @param exactSales    sales of this piece in exactly this color over 30 days
	 * @param typeSales     sales of this piece with any color of this exotic type over 30 days
	 * @param exactListings current BINs of this piece in this color
	 * @param typeListings  current BINs of this piece with this exotic type
	 */
	public record ExoticInfo(Exotic.Type type, int color, int defaultColor,
			CoflnetClient.@Nullable History exactSales, CoflnetClient.@Nullable History typeSales,
			@Nullable AuctionStats exactListings, @Nullable AuctionStats typeListings,
			double estimate, String estimateSource) {
		public String hex() {
			return String.format(java.util.Locale.ROOT, "%06X", color);
		}
	}

	public boolean known() {
		return unitValue > 0 && !Double.isNaN(unitValue);
	}

	public double totalValue() {
		return known() ? unitValue * item.count : 0;
	}

	public double upgradesRaw() {
		double t = 0;
		for (Credited c : upgrades) {
			t += c.total();
		}
		return t;
	}

	public double upgradesCredited() {
		double t = 0;
		for (Credited c : upgrades) {
			t += c.credited();
		}
		return t;
	}

	public Liquidity liquidity() {
		return Liquidity.of(dailyVolume);
	}

	/** Days until the current supply clears at the current sales rate. */
	public double daysToClear() {
		if (listed < 0 || !(dailyVolume > 0)) {
			return Double.NaN;
		}
		return (listed + item.count) / dailyVolume;
	}
}
