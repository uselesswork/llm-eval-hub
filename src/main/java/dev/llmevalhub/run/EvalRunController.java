package dev.llmevalhub.run;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/runs")
public class EvalRunController {
    private final EvalRunService service;

    public EvalRunController(EvalRunService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EvalRunResponse> create(@Valid @RequestBody CreateRunRequest request) {
        EvalRunResponse response = EvalRunResponse.from(service.create(request));
        return ResponseEntity.created(URI.create("/api/runs/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public EvalRunResponse get(@PathVariable String id) {
        EvalRun run = service.findById(id);
        if (run == null) {
            throw new ResponseStatusException(NOT_FOUND, "评测运行不存在");
        }
        return EvalRunResponse.from(run);
    }

    @GetMapping
    public List<EvalRunResponse> list(
            @RequestParam(required = false) @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sourceProject,
            @RequestParam(required = false) String datasetVersion,
            @RequestParam(required = false) String modelId,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return service.findRecent(sourceProject, datasetVersion, modelId, limit)
                .stream().map(EvalRunResponse::from).toList();
    }
}
