package dev.llmevalhub.comparison;

import dev.llmevalhub.importer.QaReportResultsResponse.CaseMetric;
import dev.llmevalhub.importer.QaReportResultsResponse.CaseResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class FailureClassifier {
    public List<FailureCode> classify(CaseResult result) {
        List<FailureCode> failures = new ArrayList<>();
        for (CaseMetric metric : result.metrics()) {
            switch (metric.key()) {
                case "qa.hit_at_k" -> {
                    if (Boolean.FALSE.equals(metric.passed())) {
                        failures.add(FailureCode.RETRIEVAL_MISS);
                    }
                }
                case "qa.citation_precision" -> {
                    if (metric.value() != null && metric.value().compareTo(BigDecimal.ONE) < 0) {
                        failures.add(FailureCode.CITATION_PRECISION_BELOW_ONE);
                    }
                }
                case "qa.answer_contains_all" -> {
                    if (Boolean.FALSE.equals(metric.passed())) {
                        failures.add(FailureCode.ANSWER_CONTAINS_FAILED);
                    }
                }
                case "qa.refusal_correct" -> {
                    if (Boolean.FALSE.equals(metric.passed())) {
                        failures.add(FailureCode.REFUSAL_INCORRECT);
                    }
                }
                default -> { }
            }
        }
        return List.copyOf(failures);
    }

    public record CaseFailures(String caseId, List<FailureCode> failures) {
    }
}
