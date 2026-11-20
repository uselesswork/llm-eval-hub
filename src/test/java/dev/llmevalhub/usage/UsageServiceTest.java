package dev.llmevalhub.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UsageServiceTest {
    @Test
    void calculatesProviderUsageWithDecimalPrices() {
        ModelPrice price = new ModelPrice("p", "model", "2026-11", "USD",
                new BigDecimal("1.25"), new BigDecimal("10.50"), Instant.EPOCH);
        assertEquals(new BigDecimal("0.022000000000"),
                UsageService.calculateCost(5000, 1500, price));
    }

    @Test
    void zeroTokenUsageIsKnownZeroCost() {
        ModelPrice price = new ModelPrice("p", "model", "2026-11", "USD",
                new BigDecimal("1.25"), new BigDecimal("10.50"), Instant.EPOCH);
        assertEquals(new BigDecimal("0E-12"), UsageService.calculateCost(0, 0, price));
    }
}
