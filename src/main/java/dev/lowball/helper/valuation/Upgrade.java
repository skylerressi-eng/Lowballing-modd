package dev.lowball.helper.valuation;

/** One applied upgrade and what it would cost to buy and apply today. */
public record Upgrade(String label, String id, double quantity, double unitPrice) {
	public double total() {
		return quantity * unitPrice;
	}
}
