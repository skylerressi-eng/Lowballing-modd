package dev.lowball.helper.market;

/**
 * Live auction house numbers for one price key, all per single item.
 *
 * @param lowest       lowest BIN, NaN if none
 * @param cleanLowest  lowest BIN without recomb/potato books/stars/gems, NaN if none
 * @param cheapest     up to 5 lowest BIN prices ascending
 * @param binCount     BIN listings
 * @param auctionCount bid (non-BIN) auctions
 */
public record AuctionStats(String key, String name, double lowest, double cleanLowest, double[] cheapest, int binCount, int auctionCount) {
	public double second() {
		return cheapest.length > 1 ? cheapest[1] : Double.NaN;
	}
}
