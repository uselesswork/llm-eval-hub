package dev.llmevalhub.usage;

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
@RequestMapping("/api")
public class UsageController {
    private final UsageService service;

    public UsageController(UsageService service) {
        this.service = service;
    }

    @PostMapping("/prices")
    public ResponseEntity<ModelPrice> createPrice(@Valid @RequestBody CreatePriceRequest request) {
        ModelPrice price = service.createPrice(request);
        return ResponseEntity.created(URI.create("/api/prices/" + price.id())).body(price);
    }

    @GetMapping("/prices")
    public List<ModelPrice> listPrices() {
        return service.listPrices();
    }

    @PostMapping("/runs/{runId}/calls")
    public ResponseEntity<ModelCall> createCall(@PathVariable String runId,
            @Valid @RequestBody CreateCallRequest request) {
        ModelCall call = service.createCall(runId, request);
        return ResponseEntity.created(URI.create("/api/runs/" + runId + "/usage")).body(call);
    }

    @GetMapping("/runs/{runId}/usage")
    public RunUsage getUsage(@PathVariable String runId) {
        return service.getRunUsage(runId);
    }
}
