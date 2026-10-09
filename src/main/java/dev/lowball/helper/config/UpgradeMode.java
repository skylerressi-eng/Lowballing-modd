package dev.lowball.helper.config;

import dev.lowball.helper.valuation.UpgradeCategory;

/** How much of the cost of applied upgrades counts towards an item's value. */
public enum UpgradeMode {
	NONE("None", "Value items as if clean", 0),
	CONSERVATIVE("Conservative", "60% of the balanced credit per category", 0.6),
	BALANCED("Balanced", "Per category: gems/scrolls/pet items 90%, recomb 80%, books/enchants 50%...", 1),
	FULL("Full cost", "Every upgrade at 100% of what it costs today", -1),
	CUSTOM("Custom", "Your own percent per category (/lowball settings)", -2);

	public final String label;
	public final String description;
	private final double factor;

	UpgradeMode(String label, String description, double factor) {
		this.label = label;
		this.description = description;
		this.factor = factor;
	}

	/** Credit 0..1 for a category under this mode. */
	public double credit(UpgradeCategory c, LowballConfig cfg) {
		if (this == FULL) {
			return 1;
		}
		if (this == CUSTOM) {
			Double v = cfg.categoryCredit == null ? null : cfg.categoryCredit.get(c.name());
			return (v != null ? v : c.defaultCredit) / 100.0;
		}
		return c.defaultCredit / 100.0 * factor;
	}
}
