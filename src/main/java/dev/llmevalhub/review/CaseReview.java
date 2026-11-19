package dev.llmevalhub.review;

import java.time.Instant;

public record CaseReview(
        String id,
        String runId,
        String caseId,
        String reviewer,
        ReviewVerdict verdict,
        String note,
        Instant recordedAt
) {
}
