package dev.llmevalhub.usage;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCallRequest(
        @NotBlank @Size(max = 128) String requestId,
        @NotBlank @Size(max = 128) String modelId,
        @Min(0) Long latencyMs,
        @Min(0) @Max(1_000_000_000L) Long inputTokens,
        @Min(0) @Max(1_000_000_000L) Long outputTokens,
        String priceVersion) {
}
