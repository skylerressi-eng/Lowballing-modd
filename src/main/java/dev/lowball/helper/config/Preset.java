package dev.lowball.helper.config;

/** How hard to lowball. The percentage is applied to the estimated value before volume/supply adjustments. */
public enum Preset {
	SNIPE("Snipe", 55, "Only take steals: clearly under value"),
	AGGRESSIVE("Aggressive", 65, "Classic lowball for quick flips"),
	STANDARD("Standard", 75, "Typical lowball most sellers accept"),
	FAIR("Fair", 85, "Quick-sell price for impatient sellers"),
	GENEROUS("Generous", 92, "Near-market offer, wins competitive trades"),
	CUSTOM("Custom", -1, "Your own percentage");

	public final String label;
	public final int percent;
	public final String description;

	Preset(String label, int percent, String description) {
		this.label = label;
		this.percent = percent;
		this.description = description;
	}

	public Preset next() {
		return values()[(ordinal() + 1) % values().length];
	}

	public Preset previous() {
		return values()[(ordinal() + values().length - 1) % values().length];
	}
}
