package dev.llmevalhub.usage;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;

public record ModelCall(String id, String runId, String requestId, String modelId,
        Long latencyMs, Long inputTokens, Long outputTokens, String priceVersion,
        String currency, @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal cost,
        String status, Instant recordedAt) {
}
