package dev.llmevalhub.job;

import dev.llmevalhub.run.EvalRunResponse;
import java.util.List;

public record JobResults(EvalRunResponse run, String sourceRunId, String reportSha256,
        int sampleCount, int passedCount, List<JobReport.Case> cases) {
}
