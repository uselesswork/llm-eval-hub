package dev.llmevalhub.importer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class QaReportParserTest {
    private final QaReportParser parser = new QaReportParser(new ObjectMapper());

    @Test
    void parsesRealFiveCaseReportWithoutInventingMissingFields() throws IOException {
        QaReportDocument report = parser.parse(fixture());

        assertEquals("baseline-2026-11-17-gpt-6-luna", report.label());
        assertEquals(5, report.sampleCount());
        assertEquals(4, report.answerableCount());
        assertEquals(1, report.refusalCount());
        assertEquals(9, report.metrics().size());
        assertEquals(5, report.cases().size());
        assertEquals("REFUSAL", report.cases().stream()
                .filter(result -> result.caseId().equals("unanswerable"))
                .findFirst().orElseThrow().sampleScope());
        assertTrue(report.corpusSha256().isEmpty());
        assertEquals("2026-11-17-gpt-6-luna.predictions.jsonl", report.predictionsFilename());
    }

    @Test
    void rejectsDuplicateCaseId() throws IOException {
        String changed = new String(fixture(), StandardCharsets.UTF_8)
                .replace("\"id\": \"cross_document\"", "\"id\": \"direct\"");
        assertThrows(InvalidQaReportException.class,
                () -> parser.parse(changed.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsMismatchedDenominator() throws IOException {
        String changed = new String(fixture(), StandardCharsets.UTF_8)
                .replace("\"sample_count\": 5", "\"sample_count\": 6");
        assertThrows(InvalidQaReportException.class,
                () -> parser.parse(changed.getBytes(StandardCharsets.UTF_8)));
    }

    static byte[] fixture() throws IOException {
        try (var stream = Objects.requireNonNull(QaReportParserTest.class.getResourceAsStream(
                "/qa-system/2026-11-17-gpt-6-luna.report.json"))) {
            return stream.readAllBytes();
        }
    }
}
