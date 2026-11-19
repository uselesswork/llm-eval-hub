package dev.llmevalhub.comparison;

import dev.llmevalhub.run.EvalRunResponse;
import java.math.BigDecimal;
import java.util.List;

public record RunComparison(
        EvalRunResponse baseline,
        EvalRunResponse candidate,
        String datasetSha256,
        int k,
        List<MetricChange> metrics,
        List<CaseChange> cases
) {
    public record MetricChange(
            String key,
            String definitionVersion,
            String aggregation,
            String sampleScope,
            int denominator,
            String unit,
            BigDecimal baselineValue,
            BigDecimal candidateValue,
            BigDecimal delta
    ) {
    }

    public record CaseChange(
            String caseId,
            String category,
            List<FailureCode> baselineFailures,
            List<FailureCode> candidateFailures,
            List<FailureCode> newFailures,
            List<FailureCode> resolvedFailures
    ) {
    }
}
