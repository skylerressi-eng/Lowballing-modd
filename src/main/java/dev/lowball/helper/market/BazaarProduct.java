package dev.lowball.helper.market;

/**
 * Bazaar numbers for one product, per unit.
 *
 * @param instaSell     what you receive selling instantly (top buy order)
 * @param instaBuy      what you pay buying instantly (top sell offer)
 * @param instaSoldWeek units sold instantly into buy orders over the last 7 days
 * @param instaBoughtWeek units bought instantly from sell offers over the last 7 days
 */
public record BazaarProduct(String id, double instaSell, double instaBuy, long instaSoldWeek, long instaBoughtWeek, int sellOffers, int buyOrders) {
	/** Insta-sell price, ignoring troll buy orders far below the spread. */
	public double safeInstaSell() {
		if (instaSell > 0 && instaBuy > 0 && instaSell < instaBuy * 0.4) {
			return instaBuy * 0.4;
		}
		return instaSell > 0 ? instaSell : instaBuy * 0.9;
	}

	/** Roughly what a patient seller gets by undercutting the lowest sell offer. */
	public double sellOfferPrice() {
		return instaBuy > 0 ? instaBuy : instaSell;
	}

	/** Units that change hands per day (both directions). */
	public double dailyVolume() {
		return (instaSoldWeek + instaBoughtWeek) / 7.0;
	}
}
