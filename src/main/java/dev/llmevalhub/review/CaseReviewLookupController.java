package dev.llmevalhub.review;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CaseReviewLookupController {
    private final CaseReviewService service;

    public CaseReviewLookupController(CaseReviewService service) {
        this.service = service;
    }

    @GetMapping("/api/reviews/{id}")
    public CaseReview get(@PathVariable String id) {
        return service.get(id);
    }
}
