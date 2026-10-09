package dev.lowball.helper.valuation;

import java.util.List;

/**
 * @param percent     final percent of value offered (0..1)
 * @param totalOffer  coins to offer for the whole stack
 * @param resaleNet   coins after selling at value and paying tax
 * @param profit      resaleNet - totalOffer
 * @param adjustments human readable reasons the percent moved
 */
public record Offer(double percent, double totalOffer, double resaleNet, double profit, List<String> adjustments) {
	public static final Offer NONE = new Offer(0, 0, 0, 0, List.of());
}
