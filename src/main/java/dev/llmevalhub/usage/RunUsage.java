package dev.llmevalhub.usage;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.util.List;

public record RunUsage(String runId, int callCount, int pricedCallCount,
        Long inputTokens, Long outputTokens, String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) BigDecimal totalCost,
        String status, List<ModelCall> calls) {
}
