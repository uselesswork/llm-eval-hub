package dev.llmevalhub.run;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateRunRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{1,64}") String sourceProject,
        @NotBlank @Size(max = 128) String runLabel,
        @NotBlank @Size(max = 128) String datasetVersion,
        @Size(max = 128) String modelId,
        @Size(max = 128) String promptVersion,
        @NotNull ResultSource resultSource,
        @NotNull RunStatus status,
        @NotNull Instant startedAt
) {
}
