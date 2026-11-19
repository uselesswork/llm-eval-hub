package dev.llmevalhub.importer;

import java.time.Instant;
import java.util.List;
import java.util.Map;

record QaReportDocument(
        String label,
        Instant createdAt,
        String pythonVersion,
        String datasetSha256,
        String predictionsSha256,
        String predictionsFilename,
        Map<String, String> corpusSha256,
        int k,
        int sampleCount,
        int answerableCount,
        int refusalCount,
        List<QaReportResultsResponse.AggregateMetric> metrics,
        List<QaReportResultsResponse.CaseResult> cases
) {
}
