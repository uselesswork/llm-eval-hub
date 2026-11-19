package dev.llmevalhub.review;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/runs/{runId}")
public class CaseReviewController {
    private final CaseReviewService service;

    public CaseReviewController(CaseReviewService service) {
        this.service = service;
    }

    @PostMapping("/cases/{caseId}/reviews")
    public ResponseEntity<CaseReview> create(@PathVariable String runId,
            @PathVariable String caseId, @Valid @RequestBody CreateReviewRequest request) {
        CaseReview review = service.create(runId, caseId, request);
        return ResponseEntity.created(URI.create("/api/reviews/" + review.id())).body(review);
    }

    @GetMapping("/reviews")
    public List<CaseReview> list(@PathVariable String runId) {
        return service.list(runId, null);
    }

    @GetMapping("/cases/{caseId}/reviews")
    public List<CaseReview> listCase(@PathVariable String runId, @PathVariable String caseId) {
        return service.list(runId, caseId);
    }
}
