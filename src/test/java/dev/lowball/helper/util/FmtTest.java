package dev.lowball.helper.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FmtTest {
	@Test
	void formatsCoinsWithoutRoundingUp() {
		assertEquals("999", Fmt.coins(999));
		assertEquals("1.5k", Fmt.coins(1_500));
		assertEquals("12.3M", Fmt.coins(12_399_999));
		assertEquals("1.99M", Fmt.coins(1_999_999));
		assertEquals("125M", Fmt.coins(125_900_000));
		assertEquals("1.25B", Fmt.coins(1_250_000_000));
		assertEquals("-2.5M", Fmt.coins(-2_500_000));
	}

	@Test
	void parsesPlayerAmounts() {
		assertEquals(1_500_000, Fmt.parse("1.5m"));
		assertEquals(150_000, Fmt.parse("150k"));
		assertEquals(2_000_000, Fmt.parse("2,000,000"));
		assertEquals(3_000_000_000d, Fmt.parse("3B"));
		assertEquals(12.5e6, Fmt.parse(" 12.5M "));
		assertTrue(Double.isNaN(Fmt.parse("abc")));
		assertTrue(Double.isNaN(Fmt.parse("1.5x")));
	}

	@Test
	void floorsToSignificantFigures() {
		assertEquals(8_730_000, Fmt.floorSig(8_734_512, 3));
		assertEquals(8_700_000, Fmt.floorSig(8_799_999, 2));
		assertEquals(990_000, Fmt.floorSig(999_999, 2));
		assertEquals(450, Fmt.floorSig(450, 3));
	}

	@Test
	void signAmountsAreShortAndExact() {
		assertEquals("8.73m", Fmt.signAmount(8_730_000));
		assertEquals("1.2b", Fmt.signAmount(1_200_000_000));
		assertEquals("950k", Fmt.signAmount(950_000));
		assertEquals("8734512", Fmt.signAmount(8_734_512));
		assertEquals("1234567890", Fmt.signAmount(1_234_567_890));
	}

	@Test
	void percentAndDays() {
		assertEquals("75%", Fmt.percent(0.75));
		assertEquals("72.5%", Fmt.percent(0.725));
		assertEquals("<1h", Fmt.days(0.01));
		assertEquals("6h", Fmt.days(0.25));
		assertEquals("2.5d", Fmt.days(2.5));
	}
}
