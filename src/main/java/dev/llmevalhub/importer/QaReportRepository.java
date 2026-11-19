package dev.llmevalhub.importer;

import dev.llmevalhub.run.EvalRun;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class QaReportRepository {
    private static final RowMapper<EvalRun> RUN_ROW = (rs, rowNum) -> new EvalRun(
            rs.getString("id"), rs.getString("source_project"), rs.getString("run_label"),
            rs.getString("dataset_version"), rs.getString("model_id"),
            rs.getString("prompt_version"), rs.getString("result_source"),
            rs.getString("status"), rs.getObject("started_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class));
    private final JdbcTemplate jdbc;

    public QaReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EvalRun findRunByReportHash(String reportSha256, boolean forUpdate) {
        String sql = """
                SELECT id, source_project, run_label, dataset_version, model_id, prompt_version,
                       result_source, status, started_at, created_at
                FROM eval_runs
                WHERE source_project = 'qa-system' AND source_report_sha256 = ?
                """ + (forUpdate ? " FOR UPDATE" : "");
        List<EvalRun> rows = jdbc.query(sql, RUN_ROW, reportSha256);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public void insertRun(EvalRun run, String reportSha256) {
        int rows = jdbc.update("""
                INSERT INTO eval_runs (id, source_project, run_label, dataset_version,
                    model_id, prompt_version, result_source, status, started_at, created_at,
                    source_report_sha256)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, run.id(), run.sourceProject(), run.runLabel(), run.datasetVersion(),
                run.modelId(), run.promptVersion(), run.resultSource(), run.status(),
                run.startedAt(), run.createdAt(), reportSha256);
        requireOne(rows, "评测运行");
    }

    public void insertReport(String runId, String reportFilename, QaReportDocument report) {
        int rows = jdbc.update("""
                INSERT INTO qa_report_imports (run_id, report_filename, dataset_sha256,
                    predictions_sha256, predictions_filename, source_created_at, python_version,
                    k, sample_count, answerable_count, refusal_count)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, runId, reportFilename, report.datasetSha256(), report.predictionsSha256(),
                report.predictionsFilename(), LocalDateTime.ofInstant(report.createdAt(), ZoneOffset.UTC),
                report.pythonVersion(), report.k(), report.sampleCount(),
                report.answerableCount(), report.refusalCount());
        requireOne(rows, "报告来源");
        for (Map.Entry<String, String> entry : report.corpusSha256().entrySet()) {
            requireOne(jdbc.update("""
                    INSERT INTO qa_report_corpus_hashes (run_id, filename, sha256) VALUES (?, ?, ?)
                    """, runId, entry.getKey(), entry.getValue()), "语料哈希");
        }
        for (QaReportResultsResponse.AggregateMetric metric : report.metrics()) {
            requireOne(jdbc.update("""
                    INSERT INTO eval_run_metrics (run_id, metric_key, definition_version,
                        aggregation, sample_scope, denominator, unit, metric_value)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, runId, metric.key(), metric.definitionVersion(), metric.aggregation(),
                    metric.sampleScope(), metric.denominator(), metric.unit(), metric.value()),
                    "汇总指标");
        }
        for (QaReportResultsResponse.CaseResult result : report.cases()) {
            requireOne(jdbc.update("""
                    INSERT INTO eval_case_results (run_id, case_id, category, sample_scope,
                        latency_ms, model_calls)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, runId, result.caseId(), result.category(), result.sampleScope(),
                    result.latencyMs(), result.modelCalls()), "逐例结果");
            for (QaReportResultsResponse.CaseMetric metric : result.metrics()) {
                requireOne(jdbc.update("""
                        INSERT INTO eval_case_metrics (run_id, case_id, metric_key,
                            definition_version, boolean_value, numeric_value)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """, runId, result.caseId(), metric.key(), metric.definitionVersion(),
                        metric.passed(), metric.value()), "逐例指标");
            }
        }
    }

    public QaReportResultsResponse.Provenance findProvenance(String runId) {
        List<QaReportResultsResponse.Provenance> rows = jdbc.query("""
                SELECT i.report_filename, r.source_report_sha256, i.dataset_sha256,
                       i.predictions_sha256, i.predictions_filename, i.source_created_at,
                       i.python_version, i.k, i.sample_count, i.answerable_count, i.refusal_count
                FROM qa_report_imports i JOIN eval_runs r ON r.id = i.run_id
                WHERE i.run_id = ?
                """, (rs, rowNum) -> new QaReportResultsResponse.Provenance(
                rs.getString("report_filename"), rs.getString("source_report_sha256"),
                rs.getString("dataset_sha256"), rs.getString("predictions_sha256"),
                rs.getString("predictions_filename"),
                rs.getObject("source_created_at", LocalDateTime.class).toInstant(ZoneOffset.UTC),
                rs.getString("python_version"), findCorpusHashes(runId), rs.getInt("k"),
                rs.getInt("sample_count"), rs.getInt("answerable_count"),
                rs.getInt("refusal_count")), runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, String> findCorpusHashes(String runId) {
        Map<String, String> hashes = new LinkedHashMap<>();
        jdbc.query("""
                SELECT filename, sha256 FROM qa_report_corpus_hashes
                WHERE run_id = ? ORDER BY filename
                """, rs -> {
                    hashes.put(rs.getString("filename"), rs.getString("sha256"));
                }, runId);
        return hashes;
    }

    public List<QaReportResultsResponse.AggregateMetric> findRunMetrics(String runId) {
        return jdbc.query("""
                SELECT metric_key, definition_version, aggregation, sample_scope,
                       denominator, unit, metric_value
                FROM eval_run_metrics WHERE run_id = ? ORDER BY metric_key
                """, (rs, rowNum) -> new QaReportResultsResponse.AggregateMetric(
                rs.getString("metric_key"), rs.getString("definition_version"),
                rs.getString("aggregation"), rs.getString("sample_scope"),
                rs.getInt("denominator"), rs.getString("unit"),
                rs.getBigDecimal("metric_value")), runId);
    }

    public List<QaReportResultsResponse.CaseResult> findCases(String runId) {
        Map<String, List<QaReportResultsResponse.CaseMetric>> metrics = new LinkedHashMap<>();
        jdbc.query("""
                SELECT case_id, metric_key, definition_version, boolean_value, numeric_value
                FROM eval_case_metrics WHERE run_id = ? ORDER BY case_id, metric_key
                """, rs -> {
                    metrics.computeIfAbsent(rs.getString("case_id"), ignored -> new ArrayList<>())
                            .add(new QaReportResultsResponse.CaseMetric(
                                    rs.getString("metric_key"), rs.getString("definition_version"),
                                    rs.getObject("boolean_value", Boolean.class),
                                    rs.getBigDecimal("numeric_value")));
                }, runId);
        return jdbc.query("""
                SELECT case_id, category, sample_scope, latency_ms, model_calls
                FROM eval_case_results WHERE run_id = ? ORDER BY case_id
                """, (rs, rowNum) -> new QaReportResultsResponse.CaseResult(
                rs.getString("case_id"), rs.getString("category"),
                rs.getString("sample_scope"), rs.getBigDecimal("latency_ms"),
                rs.getInt("model_calls"), List.copyOf(metrics.getOrDefault(
                        rs.getString("case_id"), List.of()))), runId);
    }

    private static void requireOne(int rows, String subject) {
        if (rows != 1) {
            throw new IllegalStateException(subject + "写入未影响一行");
        }
    }
}
