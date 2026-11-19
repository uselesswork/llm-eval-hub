package dev.llmevalhub.comparison;

import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RunComparisonController {
    private final RunComparisonService service;

    public RunComparisonController(RunComparisonService service) {
        this.service = service;
    }

    @GetMapping("/api/runs/{id}/classifications")
    public List<FailureClassifier.CaseFailures> classifications(@PathVariable String id) {
        return service.classify(id);
    }

    @GetMapping("/api/comparisons")
    public RunComparison compare(@RequestParam String baselineId,
            @RequestParam String candidateId) {
        return service.compare(baselineId, candidateId);
    }
}
