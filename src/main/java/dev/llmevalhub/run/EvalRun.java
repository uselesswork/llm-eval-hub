package dev.llmevalhub.run;

import java.time.LocalDateTime;

public record EvalRun(
        String id,
        String sourceProject,
        String runLabel,
        String datasetVersion,
        String modelId,
        String promptVersion,
        String resultSource,
        String status,
        LocalDateTime startedAt,
        LocalDateTime createdAt
) {
}
