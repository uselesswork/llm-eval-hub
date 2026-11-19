package dev.llmevalhub.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotBlank @Size(max = 128) String reviewer,
        @NotNull ReviewVerdict verdict,
        @Size(max = 1000) String note
) {
}
