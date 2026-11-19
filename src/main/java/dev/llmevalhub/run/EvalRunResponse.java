package dev.llmevalhub.run;

import java.time.Instant;
import java.time.ZoneOffset;

public record EvalRunResponse(
        String id,
        String sourceProject,
        String runLabel,
        String datasetVersion,
        String modelId,
        String promptVersion,
        ResultSource resultSource,
        RunStatus status,
        Instant startedAt,
        Instant createdAt
) {
    public static EvalRunResponse from(EvalRun run) {
        return new EvalRunResponse(run.id(), run.sourceProject(), run.runLabel(),
                run.datasetVersion(), run.modelId(), run.promptVersion(),
                ResultSource.valueOf(run.resultSource()), RunStatus.valueOf(run.status()),
                run.startedAt().toInstant(ZoneOffset.UTC),
                run.createdAt().toInstant(ZoneOffset.UTC));
    }
}
