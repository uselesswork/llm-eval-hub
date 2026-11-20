package dev.llmevalhub.job;

import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
public class JobReportController {
    private final JobReportService service;

    public JobReportController(JobReportService service) {
        this.service = service;
    }

    @PostMapping("/api/imports/job-agent")
    public ResponseEntity<JobReportService.ImportOutcome> importReport(
            @Valid @RequestBody JobReport report) {
        var outcome = service.importReport(report);
        return ResponseEntity.status(outcome.imported() ? HttpStatus.CREATED : HttpStatus.OK)
                .location(URI.create("/api/runs/" + outcome.runId() + "/job-results"))
                .body(outcome);
    }

    @GetMapping("/api/runs/{runId}/job-results")
    public JobResults getResults(@PathVariable String runId) {
        JobResults results = service.findResults(runId);
        if (results == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "求职助手评测结果不存在");
        return results;
    }
}
