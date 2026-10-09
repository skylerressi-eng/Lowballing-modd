package dev.lowball.helper.valuation;

/**
 * Groups of upgrades with how much of their cost a buyer typically pays back (the "Balanced" credit).
 * Removable things (gems, pet items, scrolls) keep most of their value; sunk costs (books, essence) much less.
 */
public enum UpgradeCategory {
	GEMSTONES("Gemstones", 90),
	GEM_SLOTS("Gem slot unlocks", 50),
	SCROLLS("Ability scrolls", 90),
	RECOMB("Recombobulator", 80),
	ENCHANTS("Enchantments", 50),
	STARS("Stars (essence)", 60),
	MASTER_STARS("Master stars", 80),
	POTATO_BOOKS("Potato books", 50),
	REFORGE("Reforge", 50),
	PET_ITEM("Pet items", 90),
	SKIN("Skins", 80),
	DYE("Dyes", 80),
	RUNE("Runes", 60),
	OTHER("Other upgrades", 50);

	public final String label;
	public final int defaultCredit;

	UpgradeCategory(String label, int defaultCredit) {
		this.label = label;
		this.defaultCredit = defaultCredit;
	}
}
