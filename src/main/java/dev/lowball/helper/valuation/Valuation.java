package dev.lowball.helper.valuation;

import java.util.List;

import org.jspecify.annotations.Nullable;

import dev.lowball.helper.item.SkyblockItem;
import dev.lowball.helper.market.AuctionStats;
import dev.lowball.helper.market.BazaarProduct;
import dev.lowball.helper.market.CoflnetClient;

/**
 * Everything known about what one stack is worth.
 *
 * @param unitBase      price of the base item per unit, NaN if unknown
 * @param baseSource    where {@code unitBase} came from
 * @param median        median sold price per unit (Coflnet or tracked), NaN if unknown
 * @param dailyVolume   units sold per day, NaN if unknown
 * @param listed        current listings (BINs on AH or sell offers on bazaar), -1 if unknown
 * @param upgradesRaw   full cost of applied upgrades
 * @param unitValue     base + credited upgrades, per unit
 */
public record Valuation(
		SkyblockItem item,
		boolean bazaar,
		double unitBase,
		String baseSource,
		@Nullable AuctionStats auction,
		@Nullable BazaarProduct bazaarProduct,
		CoflnetClient.@Nullable Stats coflnet,
		double median,
		double dailyVolume,
		String volumeSource,
		int listed,
		List<Upgrade> upgrades,
		double upgradesRaw,
		double upgradeCredit,
		double unitValue,
		List<String> warnings) {

	public boolean known() {
		return unitValue > 0 && !Double.isNaN(unitValue);
	}

	public double totalValue() {
		return known() ? unitValue * item.count : 0;
	}

	public double upgradesCredited() {
		return upgradesRaw * upgradeCredit;
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
