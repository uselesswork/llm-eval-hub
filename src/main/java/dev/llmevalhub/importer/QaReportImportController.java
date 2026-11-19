package dev.llmevalhub.importer;

import dev.llmevalhub.run.ResultSource;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class QaReportImportController {
    private final QaReportImportService service;

    public QaReportImportController(QaReportImportService service) {
        this.service = service;
    }

    @PostMapping(path = "/api/imports/qa-system", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<QaReportImportService.ImportOutcome> importReport(
            @RequestParam("report") MultipartFile report,
            @RequestParam("resultSource") ResultSource resultSource,
            @RequestParam(value = "modelId", required = false) String modelId) throws IOException {
        QaReportImportService.ImportOutcome outcome = service.importReport(
                report.getBytes(), report.getOriginalFilename(), resultSource, modelId);
        URI location = URI.create("/api/runs/" + outcome.runId() + "/results");
        return ResponseEntity.status(outcome.imported() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(location).body(outcome);
    }

    @GetMapping("/api/runs/{id}/results")
    public QaReportResultsResponse getResults(@PathVariable String id) {
        QaReportResultsResponse results = service.findResults(id);
        if (results == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "导入结果不存在");
        }
        return results;
    }
}
