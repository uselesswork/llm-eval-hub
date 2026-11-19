package dev.llmevalhub.importer;

import dev.llmevalhub.run.EvalRun;
import dev.llmevalhub.run.EvalRunMapper;
import dev.llmevalhub.run.EvalRunResponse;
import dev.llmevalhub.run.ResultSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class QaReportImportService {
    private final QaReportParser parser;
    private final QaReportRepository repository;
    private final EvalRunMapper runMapper;
    private final Clock clock = Clock.systemUTC();

    public QaReportImportService(QaReportParser parser, QaReportRepository repository,
            EvalRunMapper runMapper) {
        this.parser = parser;
        this.repository = repository;
        this.runMapper = runMapper;
    }

    @Transactional
    public ImportOutcome importReport(byte[] bytes, String originalFilename,
            ResultSource resultSource, String modelId) {
        if (resultSource == null || resultSource == ResultSource.HUMAN_REVIEW) {
            throw new InvalidQaReportException("结果来源必须是 LIVE_MODEL 或 OFFLINE_FIXTURE");
        }
        if (modelId != null && (modelId.isBlank() || modelId.length() > 128)) {
            throw new InvalidQaReportException("modelId 为空或超过 128 字符");
        }
        String reportFilename = QaReportParser.filename(originalFilename == null ? "" : originalFilename);
        QaReportDocument report = parser.parse(bytes);
        String reportSha = sha256(bytes);
        EvalRun existing = repository.findRunByReportHash(reportSha, false);
        if (existing != null) {
            return duplicate(existing, report, resultSource, modelId, reportSha);
        }

        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        EvalRun run = new EvalRun(UUID.randomUUID().toString(), "qa-system", report.label(),
                "sha256:" + report.datasetSha256(), modelId, null, resultSource.name(),
                "COMPLETED", LocalDateTime.ofInstant(report.createdAt(), ZoneOffset.UTC), now);
        try {
            repository.insertRun(run, reportSha);
        } catch (DuplicateKeyException exception) {
            existing = repository.findRunByReportHash(reportSha, true);
            if (existing == null) {
                throw exception;
            }
            return duplicate(existing, report, resultSource, modelId, reportSha);
        }
        repository.insertReport(run.id(), reportFilename, report);
        return new ImportOutcome(run.id(), reportSha, true);
    }

    @Transactional(readOnly = true)
    public QaReportResultsResponse findResults(String runId) {
        EvalRun run = runMapper.findById(runId);
        if (run == null) {
            return null;
        }
        QaReportResultsResponse.Provenance provenance = repository.findProvenance(runId);
        if (provenance == null) {
            return null;
        }
        return new QaReportResultsResponse(EvalRunResponse.from(run), provenance,
                repository.findRunMetrics(runId), repository.findCases(runId));
    }

    private static ImportOutcome duplicate(EvalRun existing, QaReportDocument report,
            ResultSource resultSource, String modelId, String reportSha) {
        if (!existing.runLabel().equals(report.label()) ||
                !existing.datasetVersion().equals("sha256:" + report.datasetSha256()) ||
                !Objects.equals(existing.modelId(), modelId) ||
                !existing.resultSource().equals(resultSource.name())) {
            throw new QaReportConflictException("同一报告已使用不同的模型标识或结果来源导入");
        }
        return new ImportOutcome(existing.id(), reportSha, false);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }

    public record ImportOutcome(String runId, String reportSha256, boolean imported) {
    }
}
