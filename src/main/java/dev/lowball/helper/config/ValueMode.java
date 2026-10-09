package dev.lowball.helper.config;

/** What "value" means for an auction-house item. */
public enum ValueMode {
	SMART("Smart", "Clean LBIN, falling back to recent sale median when the LBIN looks manipulated or stale"),
	LBIN("Lowest BIN", "Cheapest BIN currently on the auction house"),
	MEDIAN("Sold median", "Median sale price over the last day (Coflnet)"),
	LOWEST("Lowest of both", "The lower of LBIN and sold median: safest");

	public final String label;
	public final String description;

	ValueMode(String label, String description) {
		this.label = label;
		this.description = description;
	}
}
