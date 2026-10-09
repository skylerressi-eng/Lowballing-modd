package dev.lowball.helper.valuation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AhTaxTest {
	@Test
	void bracketsMatchHypixel() {
		assertEquals(500_000 * 0.99, AhTax.net(500_000), 1e-6);          // 1% listing, no claim tax
		assertEquals(5_000_000 * 0.98, AhTax.net(5_000_000), 1e-6);      // 1% + 1% claim
		assertEquals(50_000_000 * 0.97, AhTax.net(50_000_000), 1e-6);    // 2% + 1%
		assertEquals(500_000_000 * 0.965, AhTax.net(500_000_000), 1e-6); // 2.5% + 1%
		assertEquals(0, AhTax.net(0));
	}
}
