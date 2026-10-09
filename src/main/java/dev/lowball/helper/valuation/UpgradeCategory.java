package dev.lowball.helper.valuation;

/**
 * Groups of upgrades with how much of their cost a buyer typically pays back (the "Balanced" credit).
 * Removable things (gems, pet items, scrolls) keep most of their value; sunk costs (books, essence) much less.
 */
public enum UpgradeCategory {
	GEMSTONES("Gemstones", 90),
	GEM_SLOTS("Gem slot unlocks", 40),
	SCROLLS("Ability scrolls", 90),
	RECOMB("Recombobulator", 75),
	ENCHANTS("Enchantments", 40),
	STARS("Stars (essence)", 55),
	MASTER_STARS("Master stars", 75),
	POTATO_BOOKS("Potato books", 35),
	REFORGE("Reforge", 30),
	PET_ITEM("Pet items", 90),
	SKIN("Skins", 75),
	DYE("Dyes", 75),
	RUNE("Runes", 50),
	OTHER("Other upgrades", 45);

	public final String label;
	public final int defaultCredit;

	UpgradeCategory(String label, int defaultCredit) {
		this.label = label;
		this.defaultCredit = defaultCredit;
	}
}
