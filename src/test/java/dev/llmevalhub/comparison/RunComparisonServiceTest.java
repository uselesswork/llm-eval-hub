package dev.llmevalhub.comparison;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import dev.llmevalhub.importer.QaReportImportService;
import dev.llmevalhub.importer.QaReportResultsResponse;
import dev.llmevalhub.run.EvalRunResponse;
import dev.llmevalhub.run.ResultSource;
import dev.llmevalhub.run.RunStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class RunComparisonServiceTest {
    @Mock
    QaReportImportService reports;

    @Test
    void comparesMatchingMetricsAndFindsNewFailure() {
        when(reports.findResults("before")).thenReturn(report("before", "dataset-a", 1,
                ResultSource.LIVE_MODEL, true));
        when(reports.findResults("after")).thenReturn(report("after", "dataset-a", 1,
                ResultSource.LIVE_MODEL, false));

        RunComparison result = new RunComparisonService(reports, new FailureClassifier())
                .compare("before", "after");

        assertEquals(new BigDecimal("-1"), result.metrics().get(0).delta());
        assertEquals(List.of(FailureCode.ANSWER_CONTAINS_FAILED),
                result.cases().get(0).newFailures());
        assertEquals(List.of(), result.cases().get(0).resolvedFailures());
    }

    @Test
    void rejectsDifferentDatasetEvenWithSameCaseId() {
        when(reports.findResults("before")).thenReturn(report("before", "dataset-a", 1,
                ResultSource.LIVE_MODEL, true));
        when(reports.findResults("after")).thenReturn(report("after", "dataset-b", 1,
                ResultSource.LIVE_MODEL, false));

        assertThrows(ResponseStatusException.class, () ->
                new RunComparisonService(reports, new FailureClassifier())
                        .compare("before", "after"));
    }

    @Test
    void rejectsDifferentMetricDenominators() {
        when(reports.findResults("before")).thenReturn(report("before", "dataset-a", 1,
                ResultSource.LIVE_MODEL, true));
        when(reports.findResults("after")).thenReturn(report("after", "dataset-a", 2,
                ResultSource.LIVE_MODEL, false));

        assertThrows(ResponseStatusException.class, () ->
                new RunComparisonService(reports, new FailureClassifier())
                        .compare("before", "after"));
    }

    @Test
    void rejectsMixingLiveAndOfflineResults() {
        when(reports.findResults("before")).thenReturn(report("before", "dataset-a", 1,
                ResultSource.LIVE_MODEL, true));
        when(reports.findResults("after")).thenReturn(report("after", "dataset-a", 1,
                ResultSource.OFFLINE_FIXTURE, false));

        assertThrows(ResponseStatusException.class, () ->
                new RunComparisonService(reports, new FailureClassifier())
                        .compare("before", "after"));
    }

    private static QaReportResultsResponse report(String id, String dataset,
            int denominator, ResultSource source, boolean passed) {
        Instant time = Instant.parse("2026-11-19T00:00:00Z");
        EvalRunResponse run = new EvalRunResponse(id, "qa-system", id, dataset,
                "model", null, source, RunStatus.COMPLETED, time, time);
        QaReportResultsResponse.Provenance provenance = new QaReportResultsResponse.Provenance(
                "report.json", "a".repeat(64), dataset, "b".repeat(64),
                "predictions.jsonl", time, "3.13", Map.of(), 5, 1, 1, 0);
        QaReportResultsResponse.AggregateMetric metric =
                new QaReportResultsResponse.AggregateMetric("qa.answer_contains_accuracy",
                        "qa-system.eval.metrics.v1", "MEAN", "ANSWERABLE", denominator,
                        "RATIO", passed ? BigDecimal.ONE : BigDecimal.ZERO);
        QaReportResultsResponse.CaseResult result = new QaReportResultsResponse.CaseResult(
                "case-a", "direct", "ANSWERABLE", BigDecimal.TEN, 1,
                List.of(new QaReportResultsResponse.CaseMetric("qa.answer_contains_all",
                        "qa-system.eval.metrics.v1", passed, null)));
        return new QaReportResultsResponse(run, provenance, List.of(metric), List.of(result));
    }
}
