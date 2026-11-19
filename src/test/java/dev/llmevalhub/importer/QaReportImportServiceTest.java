package dev.llmevalhub.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.llmevalhub.run.EvalRun;
import dev.llmevalhub.run.EvalRunMapper;
import dev.llmevalhub.run.ResultSource;
import java.io.IOException;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class QaReportImportServiceTest {
    @Mock
    QaReportRepository repository;

    @Mock
    EvalRunMapper runMapper;

    @Test
    void importingSameReportTwiceReturnsExistingRunWithoutSecondWrite() throws IOException {
        QaReportImportService service = new QaReportImportService(
                new QaReportParser(new ObjectMapper()), repository, runMapper);
        byte[] report = QaReportParserTest.fixture();

        QaReportImportService.ImportOutcome first = service.importReport(report,
                "2026-11-17-gpt-6-luna.report.json", ResultSource.LIVE_MODEL, "gpt-6-luna");
        when(repository.findRunByReportHash(first.reportSha256(), false)).thenReturn(
                new EvalRun(first.runId(), "qa-system", "baseline-2026-11-17-gpt-6-luna",
                        "sha256:23c74b05396a1a40d9e0d910075ac85f256c6f76fc8b7ef9a6bfd5f320ca609d",
                        "gpt-6-luna", null, "LIVE_MODEL", "COMPLETED",
                        LocalDateTime.parse("2026-11-17T04:06:15"),
                        LocalDateTime.parse("2026-11-19T00:00:00")));

        QaReportImportService.ImportOutcome second = service.importReport(report,
                "copy.report.json", ResultSource.LIVE_MODEL, "gpt-6-luna");

        assertTrue(first.imported());
        assertFalse(second.imported());
        assertEquals(first.runId(), second.runId());
        assertEquals(first.reportSha256(), second.reportSha256());
        verify(repository, times(1)).insertRun(any(EvalRun.class), eq(first.reportSha256()));
        verify(repository, times(1)).insertReport(eq(first.runId()),
                eq("2026-11-17-gpt-6-luna.report.json"), any(QaReportDocument.class));
    }

    @Test
    void rejectsConflictingProvenanceForSameReport() throws IOException {
        QaReportImportService service = new QaReportImportService(
                new QaReportParser(new ObjectMapper()), repository, runMapper);
        byte[] report = QaReportParserTest.fixture();
        QaReportImportService.ImportOutcome first = service.importReport(report,
                "baseline.report.json", ResultSource.LIVE_MODEL, "gpt-6-luna");
        when(repository.findRunByReportHash(first.reportSha256(), false)).thenReturn(
                new EvalRun(first.runId(), "qa-system", "baseline-2026-11-17-gpt-6-luna",
                        "sha256:23c74b05396a1a40d9e0d910075ac85f256c6f76fc8b7ef9a6bfd5f320ca609d",
                        "gpt-6-luna", null, "LIVE_MODEL", "COMPLETED",
                        LocalDateTime.parse("2026-11-17T04:06:15"),
                        LocalDateTime.parse("2026-11-19T00:00:00")));

        assertThrows(QaReportConflictException.class,
                () -> service.importReport(report, "baseline.report.json",
                        ResultSource.LIVE_MODEL, "other-model"));
        verify(repository, times(1)).insertRun(any(EvalRun.class), eq(first.reportSha256()));
    }
}
