package dev.llmevalhub.usage;

import java.math.BigDecimal;
import java.time.Instant;

public record ModelPrice(String id, String modelId, String version, String currency,
        BigDecimal inputPerMillion, BigDecimal outputPerMillion, Instant createdAt) {
}
