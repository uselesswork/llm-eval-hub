package dev.llmevalhub.comparison;

import dev.llmevalhub.importer.QaReportImportService;
import dev.llmevalhub.importer.QaReportResultsResponse;
import dev.llmevalhub.importer.QaReportResultsResponse.AggregateMetric;
import dev.llmevalhub.importer.QaReportResultsResponse.CaseMetric;
import dev.llmevalhub.importer.QaReportResultsResponse.CaseResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RunComparisonService {
    private final QaReportImportService reports;
    private final FailureClassifier classifier;

    public RunComparisonService(QaReportImportService reports, FailureClassifier classifier) {
        this.reports = reports;
        this.classifier = classifier;
    }

    public List<FailureClassifier.CaseFailures> classify(String runId) {
        QaReportResultsResponse report = report(runId);
        return report.cases().stream()
                .map(result -> new FailureClassifier.CaseFailures(result.caseId(),
                        classifier.classify(result)))
                .toList();
    }

    public RunComparison compare(String baselineId, String candidateId) {
        if (baselineId.equals(candidateId)) {
            throw incompatible("请选择两个不同的运行");
        }
        QaReportResultsResponse before = report(baselineId);
        QaReportResultsResponse after = report(candidateId);
        if (!before.run().sourceProject().equals(after.run().sourceProject()) ||
                !before.run().datasetVersion().equals(after.run().datasetVersion()) ||
                before.run().resultSource() != after.run().resultSource() ||
                !before.provenance().datasetSha256().equals(after.provenance().datasetSha256()) ||
                before.provenance().k() != after.provenance().k() ||
                before.provenance().sampleCount() != after.provenance().sampleCount() ||
                before.provenance().answerableCount() != after.provenance().answerableCount() ||
                before.provenance().refusalCount() != after.provenance().refusalCount()) {
            throw incompatible("项目、结果来源、案例集哈希、k 或样本分母不一致");
        }

        Map<String, AggregateMetric> afterMetrics = metricMap(after.metrics());
        if (before.metrics().size() != after.metrics().size() ||
                !afterMetrics.keySet().equals(metricMap(before.metrics()).keySet())) {
            throw incompatible("汇总指标集合不一致");
        }
        List<RunComparison.MetricChange> metricChanges = new ArrayList<>();
        for (AggregateMetric oldMetric : before.metrics()) {
            AggregateMetric newMetric = afterMetrics.get(oldMetric.key());
            if (!oldMetric.definitionVersion().equals(newMetric.definitionVersion()) ||
                    !oldMetric.aggregation().equals(newMetric.aggregation()) ||
                    !oldMetric.sampleScope().equals(newMetric.sampleScope()) ||
                    oldMetric.denominator() != newMetric.denominator() ||
                    !oldMetric.unit().equals(newMetric.unit()) ||
                    ((oldMetric.value() == null) != (newMetric.value() == null))) {
                throw incompatible("指标定义或分母不一致：" + oldMetric.key());
            }
            BigDecimal delta = oldMetric.value() == null ? null :
                    newMetric.value().subtract(oldMetric.value());
            metricChanges.add(new RunComparison.MetricChange(oldMetric.key(),
                    oldMetric.definitionVersion(), oldMetric.aggregation(),
                    oldMetric.sampleScope(), oldMetric.denominator(), oldMetric.unit(),
                    oldMetric.value(), newMetric.value(), delta));
        }

        Map<String, CaseResult> afterCases = caseMap(after.cases());
        if (before.cases().size() != after.cases().size() ||
                !afterCases.keySet().equals(caseMap(before.cases()).keySet())) {
            throw incompatible("案例 ID 集合不一致");
        }
        List<RunComparison.CaseChange> caseChanges = new ArrayList<>();
        for (CaseResult oldCase : before.cases()) {
            CaseResult newCase = afterCases.get(oldCase.caseId());
            if (!oldCase.category().equals(newCase.category()) ||
                    !oldCase.sampleScope().equals(newCase.sampleScope()) ||
                    !caseMetricSchema(oldCase).equals(caseMetricSchema(newCase))) {
                throw incompatible("案例类别、题型或逐例指标定义不一致：" + oldCase.caseId());
            }
            List<FailureCode> oldFailures = classifier.classify(oldCase);
            List<FailureCode> newFailures = classifier.classify(newCase);
            caseChanges.add(new RunComparison.CaseChange(oldCase.caseId(), oldCase.category(),
                    oldFailures, newFailures,
                    newFailures.stream().filter(code -> !oldFailures.contains(code)).toList(),
                    oldFailures.stream().filter(code -> !newFailures.contains(code)).toList()));
        }
        return new RunComparison(before.run(), after.run(), before.provenance().datasetSha256(),
                before.provenance().k(), metricChanges, caseChanges);
    }

    private QaReportResultsResponse report(String id) {
        QaReportResultsResponse response = reports.findResults(id);
        if (response == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入结果不存在：" + id);
        }
        return response;
    }

    private static Map<String, AggregateMetric> metricMap(List<AggregateMetric> metrics) {
        Map<String, AggregateMetric> map = new LinkedHashMap<>();
        metrics.forEach(metric -> map.put(metric.key(), metric));
        return map;
    }

    private static Map<String, CaseResult> caseMap(List<CaseResult> cases) {
        Map<String, CaseResult> map = new LinkedHashMap<>();
        cases.forEach(result -> map.put(result.caseId(), result));
        return map;
    }

    private static Map<String, String> caseMetricSchema(CaseResult result) {
        Map<String, String> map = new LinkedHashMap<>();
        for (CaseMetric metric : result.metrics()) {
            map.put(metric.key(), metric.definitionVersion() + ":" +
                    (metric.passed() != null ? "BOOLEAN" : "NUMERIC"));
        }
        return map;
    }

    private static ResponseStatusException incompatible(String reason) {
        return new ResponseStatusException(HttpStatus.CONFLICT, reason);
    }
}
