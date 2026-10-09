package dev.lowball.helper.util;

import java.util.regex.Pattern;

public final class Text {
	private static final Pattern FORMATTING = Pattern.compile("(?i)§[0-9a-fk-or]");

	private Text() {
	}

	public static String strip(String s) {
		return s == null ? "" : FORMATTING.matcher(s).replaceAll("");
	}

	/** "ULTIMATE_WISE" → "Ultimate Wise". */
	public static String prettyId(String id) {
		StringBuilder sb = new StringBuilder();
		for (String part : id.toLowerCase(java.util.Locale.ROOT).split("[_ ]+")) {
			if (part.isEmpty()) {
				continue;
			}
			if (!sb.isEmpty()) {
				sb.append(' ');
			}
			sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
		}
		return sb.toString();
	}

	public static String roman(int n) {
		String[] r = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
		return n >= 0 && n < r.length ? r[n] : Integer.toString(n);
	}
}
