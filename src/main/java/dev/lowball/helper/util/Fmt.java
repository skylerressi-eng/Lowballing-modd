package dev.lowball.helper.util;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Coin formatting and parsing in the style SkyBlock players use ("1.25M", "850k", "2b"). */
public final class Fmt {
	private static final Pattern AMOUNT = Pattern.compile("^\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*([kmbt])?\\s*$", Pattern.CASE_INSENSITIVE);

	private Fmt() {
	}

	/** Short form: 999, 1.2k, 15.3k, 1.25M, 12.5M, 125M, 1.25B. */
	public static String coins(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			return "?";
		}
		double abs = Math.abs(value);
		String sign = value < 0 ? "-" : "";
		if (abs < 1_000) {
			return sign + trim(abs < 10 ? round(abs, 1) : Math.floor(abs));
		}
		String[] suffixes = {"k", "M", "B", "T"};
		double scaled = abs;
		int idx = -1;
		while (scaled >= 1000 && idx < suffixes.length - 1) {
			scaled /= 1000;
			idx++;
		}
		// three significant digits, never rounding up past what the value is
		double shown;
		if (scaled >= 100) {
			shown = Math.floor(scaled);
		} else if (scaled >= 10) {
			shown = Math.floor(scaled * 10) / 10;
		} else {
			shown = Math.floor(scaled * 100) / 100;
		}
		return sign + trim(shown) + suffixes[idx];
	}

	/** Full form with thousands separators: 12,345,678. */
	public static String full(double value) {
		return String.format(Locale.ROOT, "%,d", Math.round(value));
	}

	/** Amount suitable for typing into the Hypixel coin sign (Hypixel accepts "12.5m"). */
	public static String signAmount(double value) {
		double abs = Math.abs(value);
		if (abs >= 1_000_000_000 && isClean(abs / 1_000_000_000)) {
			return trim(round(abs / 1_000_000_000, 3)) + "b";
		}
		if (abs >= 1_000_000 && abs < 1_000_000_000 && isClean(abs / 1_000_000)) {
			return trim(round(abs / 1_000_000, 3)) + "m";
		}
		if (abs >= 1_000 && abs < 1_000_000 && isClean(abs / 1_000)) {
			return trim(round(abs / 1_000, 3)) + "k";
		}
		return Long.toString(Math.round(abs));
	}

	public static String percent(double fraction) {
		double pct = fraction * 100;
		return (Math.abs(pct - Math.round(pct)) < 0.05 ? Long.toString(Math.round(pct)) : String.format(Locale.ROOT, "%.1f", pct)) + "%";
	}

	public static String volume(double perDay) {
		if (perDay < 0) {
			return "?";
		}
		if (perDay >= 1000) {
			return coins(perDay);
		}
		if (perDay >= 10) {
			return Long.toString(Math.round(perDay));
		}
		return trim(round(perDay, 1));
	}

	/** Parses "1.5m", "150k", "2,000,000", "3B". Returns NaN when the text is not an amount. */
	public static double parse(String text) {
		if (text == null) {
			return Double.NaN;
		}
		Matcher m = AMOUNT.matcher(text);
		if (!m.matches()) {
			return Double.NaN;
		}
		double base;
		try {
			base = Double.parseDouble(m.group(1).replace(",", ""));
		} catch (NumberFormatException e) {
			return Double.NaN;
		}
		String suffix = m.group(2);
		if (suffix == null) {
			return base;
		}
		return switch (suffix.toLowerCase(Locale.ROOT)) {
			case "k" -> base * 1e3;
			case "m" -> base * 1e6;
			case "b" -> base * 1e9;
			case "t" -> base * 1e12;
			default -> base;
		};
	}

	/** Rounds down to the given number of significant figures (a lowball never rounds up). */
	public static double floorSig(double value, int sig) {
		if (value <= 0 || sig <= 0) {
			return Math.max(0, value);
		}
		int digits = (int) Math.floor(Math.log10(value)) + 1;
		double unit = Math.pow(10, Math.max(0, digits - sig));
		return Math.floor(value / unit + 1e-9) * unit;
	}

	public static String ago(long millis) {
		if (millis <= 0) {
			return "never";
		}
		long s = (System.currentTimeMillis() - millis) / 1000;
		if (s < 60) {
			return Math.max(0, s) + "s ago";
		}
		if (s < 3600) {
			return s / 60 + "m ago";
		}
		if (s < 86400) {
			return s / 3600 + "h ago";
		}
		return s / 86400 + "d ago";
	}

	public static String days(double days) {
		if (Double.isNaN(days) || Double.isInfinite(days)) {
			return "?";
		}
		if (days < 1.0 / 24) {
			return "<1h";
		}
		if (days < 1) {
			return Math.round(days * 24) + "h";
		}
		if (days >= 99) {
			return "99d+";
		}
		return trim(round(days, days < 10 ? 1 : 0)) + "d";
	}

	private static boolean isClean(double scaled) {
		return Math.abs(scaled * 1000 - Math.round(scaled * 1000)) < 1e-6;
	}

	private static double round(double v, int places) {
		double f = Math.pow(10, places);
		return Math.round(v * f) / f;
	}

	private static String trim(double v) {
		if (v == Math.rint(v)) {
			return Long.toString((long) v);
		}
		String s = String.format(Locale.ROOT, "%.3f", v);
		s = s.replaceAll("0+$", "");
		return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
	}
}
