package dev.lowball.helper.valuation;

public enum Liquidity {
	FAST("Fast", 0xFF55FF55),
	OK("Steady", 0xFFFFFF55),
	SLOW("Slow", 0xFFFFAA00),
	ILLIQUID("Illiquid", 0xFFFF5555),
	UNKNOWN("Unknown", 0xFFAAAAAA);

	public final String label;
	public final int color;

	Liquidity(String label, int color) {
		this.label = label;
		this.color = color;
	}

	public static Liquidity of(double perDay) {
		if (Double.isNaN(perDay) || perDay < 0) {
			return UNKNOWN;
		}
		if (perDay >= 20) {
			return FAST;
		}
		if (perDay >= 5) {
			return OK;
		}
		if (perDay >= 1) {
			return SLOW;
		}
		return ILLIQUID;
	}
}
