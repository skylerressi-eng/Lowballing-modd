package dev.lowball.helper.item;

import java.util.Set;

/**
 * Exotic leather armor: a dye color the piece can't normally have and that didn't come from a dye item.
 * Categories follow the community names (and Coflnet's {@code ExoticColor} filter).
 */
public final class Exotic {
	public enum Type {
		NONE("", ""),
		EXOTIC("Exotic", "Exotic"),
		CRYSTAL("Crystal", "Crystal"),
		FAIRY("Fairy", "Fairy"),
		OG_FAIRY("OG Fairy", "OG Fairy"),
		SPOOK("Spook", "Spook"),
		GLITCHED("Glitched", "Glitched");

		public final String label;
		/** Value of Coflnet's ExoticColor filter. */
		public final String coflnet;

		Type(String label, String coflnet) {
			this.label = label;
			this.coflnet = coflnet;
		}
	}

	static final Set<String> CRYSTAL = Set.of("1F0030", "46085E", "54146E", "5D1C78", "63237D", "6A2C82", "7E4196", "8E51A6",
			"9C64B3", "A875BD", "B88BC9", "C6A3D4", "D9C1E3", "E5D1ED", "EFE1F5", "FCF3FF");
	static final Set<String> FAIRY = Set.of("330066", "4C0099", "660033", "6600CC", "7F00FF", "99004C", "9933FF", "B266FF",
			"CC0066", "CC99FF", "E5CCFF", "FF007F", "FF3399", "FF66B2", "FF99CC", "FFCCE5");
	static final Set<String> OG_FAIRY = Set.of("FF99FF", "FFCCFF", "CC00CC", "FF00FF", "FF33FF", "FF66FF");
	static final Set<String> SPOOK = Set.of("000000", "070008", "0E000F", "150017", "1B001F", "220027", "29002E", "300036",
			"37003E", "3E0046", "45004D", "4C0055", "52005D", "590065", "60006C", "670074", "6E007C", "750084", "7C008B",
			"830093", "89009B", "9000A3", "9700AA", "993399", "9E00B2");
	/** Seymour's special pieces roll random colors, so their colors mean nothing. */
	private static final Set<String> RANDOM_COLOR_ITEMS = Set.of("VELVET_TOP_HAT", "CASHMERE_JACKET", "SATIN_TROUSERS", "OXFORD_SHOES");

	private Exotic() {
	}

	/** @param defaultColor the piece's normal color (0xRRGGBB) from item data, or -1 when unknown */
	public static Type classify(SkyblockItem item, int defaultColor) {
		if (item.color < 0 || defaultColor < 0 || item.dyeItem != null || RANDOM_COLOR_ITEMS.contains(item.id)) {
			return Type.NONE;
		}
		if ((defaultColor & 0xFFFFFF) == item.color) {
			return Type.NONE;
		}
		String hex = item.colorHex();
		if (CRYSTAL.contains(hex)) {
			return item.id.startsWith("CRYSTAL_") ? Type.NONE : Type.CRYSTAL;
		}
		if (item.id.startsWith("FAIRY_")) {
			// fairy pieces legitimately reroll through the fairy palette
			if (FAIRY.contains(hex)) {
				return Type.NONE;
			}
			return OG_FAIRY.contains(hex) ? Type.OG_FAIRY : Type.EXOTIC;
		}
		if (FAIRY.contains(hex)) {
			return Type.FAIRY;
		}
		if (SPOOK.contains(hex) && !item.id.startsWith("GREAT_SPOOK")) {
			return Type.SPOOK;
		}
		return Type.EXOTIC;
	}

	/** "255,215,0" (Hypixel item data format) → 0xFFD700, or -1. */
	public static int parseRgb(String rgb) {
		if (rgb == null) {
			return -1;
		}
		String[] p = rgb.split(",");
		if (p.length != 3) {
			return -1;
		}
		try {
			return (Integer.parseInt(p[0].trim()) << 16) | (Integer.parseInt(p[1].trim()) << 8) | Integer.parseInt(p[2].trim());
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	/** Coflnet's Color filter format: "31:0:48". */
	public static String coflnetColor(int rgb) {
		return ((rgb >> 16) & 0xFF) + ":" + ((rgb >> 8) & 0xFF) + ":" + (rgb & 0xFF);
	}
}
