package dev.llmevalhub.job;

import dev.llmevalhub.run.EvalRun;
import dev.llmevalhub.run.EvalRunResponse;
import dev.llmevalhub.run.ResultSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class JobReportService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Clock clock = Clock.systemUTC();

    public JobReportService(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    @Transactional
    public ImportOutcome importReport(JobReport report) {
        validate(report);
        String hash = hash(report);
        List<Existing> existing = findExisting(report.sourceRunId());
        if (!existing.isEmpty()) return duplicate(existing.get(0), hash);
        String id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        try {
            jdbc.update("""
                    INSERT INTO eval_runs (id, source_project, run_label, dataset_version,
                        model_id, prompt_version, result_source, status, started_at, created_at,
                        source_report_sha256) VALUES (?, 'job-agent', ?, ?, ?, NULL, ?, 'COMPLETED', ?, ?, ?)
                    """, id, report.runLabel(), "sha256:" + report.datasetSha256().toLowerCase(),
                    report.modelId(), report.resultSource().name(),
                    LocalDateTime.ofInstant(report.startedAt(), ZoneOffset.UTC), now, hash);
            int passed = (int) report.cases().stream().filter(JobReport.Case::passed).count();
            jdbc.update("""
                    INSERT INTO job_report_imports (run_id, source_run_id, report_sha256,
                        sample_count, passed_count) VALUES (?, ?, ?, ?, ?)
                    """, id, report.sourceRunId(), hash, report.cases().size(), passed);
            for (JobReport.Case item : report.cases()) {
                jdbc.update("""
                        INSERT INTO job_case_results (run_id, case_id, task, passed)
                        VALUES (?, ?, ?, ?)
                        """, id, item.id(), item.task(), item.passed());
                for (JobReport.Check check : item.checks()) {
                    jdbc.update("""
                            INSERT INTO job_case_checks (run_id, case_id, check_name, passed)
                            VALUES (?, ?, ?, ?)
                            """, id, item.id(), check.name(), check.passed());
                }
            }
            return new ImportOutcome(id, hash, true);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "源运行或报告哈希已存在，请按原文件重试", e);
        }
    }

    @Transactional(readOnly = true)
    public JobResults findResults(String runId) {
        List<JobResults> rows = jdbc.query("""
                SELECT r.*, j.source_run_id, j.report_sha256, j.sample_count, j.passed_count
                FROM eval_runs r JOIN job_report_imports j ON j.run_id = r.id WHERE r.id = ?
                """, (rs, row) -> {
                    EvalRun run = new EvalRun(rs.getString("id"), rs.getString("source_project"),
                            rs.getString("run_label"), rs.getString("dataset_version"),
                            rs.getString("model_id"), rs.getString("prompt_version"),
                            rs.getString("result_source"), rs.getString("status"),
                            rs.getObject("started_at", LocalDateTime.class),
                            rs.getObject("created_at", LocalDateTime.class));
                    return new JobResults(EvalRunResponse.from(run), rs.getString("source_run_id"),
                            rs.getString("report_sha256"), rs.getInt("sample_count"),
                            rs.getInt("passed_count"), findCases(runId));
                }, runId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private List<JobReport.Case> findCases(String runId) {
        return jdbc.query("""
                SELECT case_id, task, passed FROM job_case_results
                WHERE run_id = ? ORDER BY case_id
                """, (rs, row) -> new JobReport.Case(rs.getString("case_id"),
                rs.getString("task"), rs.getBoolean("passed"), jdbc.query("""
                        SELECT check_name, passed FROM job_case_checks
                        WHERE run_id = ? AND case_id = ? ORDER BY check_name
                        """, (checkRs, checkRow) -> new JobReport.Check(
                        checkRs.getString("check_name"), checkRs.getBoolean("passed")),
                        runId, rs.getString("case_id"))), runId);
    }

    private List<Existing> findExisting(String sourceRunId) {
        return jdbc.query("""
                SELECT run_id, report_sha256 FROM job_report_imports WHERE source_run_id = ?
                """, (rs, row) -> new Existing(rs.getString("run_id"),
                rs.getString("report_sha256")), sourceRunId);
    }

    private static ImportOutcome duplicate(Existing existing, String hash) {
        if (!existing.hash().equals(hash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "同一源运行 ID 的报告内容不同");
        }
        return new ImportOutcome(existing.runId(), hash, false);
    }

    private static void validate(JobReport report) {
        if (report.resultSource() == ResultSource.HUMAN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "结果来源不能是人工复核");
        }
        if (report.resultSource() == ResultSource.OFFLINE_FIXTURE && report.modelId() != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "离线夹具不能声明真实模型");
        }
        HashSet<String> ids = new HashSet<>();
        for (JobReport.Case item : report.cases()) {
            if (!ids.add(item.id())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "案例 ID 重复");
            HashSet<String> names = new HashSet<>();
            for (JobReport.Check check : item.checks()) {
                if (!names.add(check.name())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "检查名重复");
            }
            if (item.passed() != item.checks().stream().allMatch(JobReport.Check::passed)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "案例结论与检查结果不一致");
            }
        }
    }

    private String hash(JobReport report) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(report)));
        } catch (NoSuchAlgorithmException | JacksonException e) {
            throw new IllegalStateException("无法计算报告哈希", e);
        }
    }

    private record Existing(String runId, String hash) { }
    public record ImportOutcome(String runId, String reportSha256, boolean imported) { }
}
