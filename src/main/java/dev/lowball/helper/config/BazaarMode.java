package dev.lowball.helper.config;

public enum BazaarMode {
	SELL_OFFER("Sell offer", "What you get listing a sell offer (patient)"),
	INSTA_SELL("Insta-sell", "What you get selling instantly (safe)");

	public final String label;
	public final String description;

	BazaarMode(String label, String description) {
		this.label = label;
		this.description = description;
	}
}
