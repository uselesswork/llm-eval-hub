package dev.llmevalhub.importer;

import dev.llmevalhub.run.EvalRunResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record QaReportResultsResponse(
        EvalRunResponse run,
        Provenance provenance,
        List<AggregateMetric> metrics,
        List<CaseResult> cases
) {
    public record Provenance(
            String reportFilename,
            String reportSha256,
            String datasetSha256,
            String predictionsSha256,
            String predictionsFilename,
            Instant reportCreatedAt,
            String pythonVersion,
            Map<String, String> corpusSha256,
            int k,
            int sampleCount,
            int answerableCount,
            int refusalCount
    ) {
    }

    public record AggregateMetric(
            String key,
            String definitionVersion,
            String aggregation,
            String sampleScope,
            int denominator,
            String unit,
            BigDecimal value
    ) {
    }

    public record CaseResult(
            String caseId,
            String category,
            String sampleScope,
            BigDecimal latencyMs,
            int modelCalls,
            List<CaseMetric> metrics
    ) {
    }

    public record CaseMetric(
            String key,
            String definitionVersion,
            Boolean passed,
            BigDecimal value
    ) {
    }
}
