package dev.lowball.helper.valuation;

/** Auction house fees for a BIN sale: listing fee by price bracket plus the 1% claim tax above 1M. */
public final class AhTax {
	private AhTax() {
	}

	public static double listingFeeRate(double price) {
		if (price < 10_000_000) {
			return 0.01;
		}
		if (price < 100_000_000) {
			return 0.02;
		}
		return 0.025;
	}

	public static double claimTaxRate(double price) {
		return price > 1_000_000 ? 0.01 : 0;
	}

	/** Coins in your purse after listing and selling at {@code price}. */
	public static double net(double price) {
		if (!(price > 0)) {
			return 0;
		}
		return price * (1 - listingFeeRate(price) - claimTaxRate(price));
	}
}
