package dev.llmevalhub.job;

import dev.llmevalhub.run.ResultSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public record JobReport(
        @NotBlank @Size(max = 128) String sourceRunId,
        @NotBlank @Pattern(regexp = "[a-fA-F0-9]{64}") String datasetSha256,
        @NotBlank @Size(max = 128) String runLabel,
        @NotNull ResultSource resultSource,
        @Size(max = 128) String modelId,
        @NotNull Instant startedAt,
        @NotEmpty @Size(max = 1000) List<@Valid Case> cases) {
    public record Case(@NotBlank @Size(max = 128) String id,
            @NotBlank @Pattern(regexp = "resume|job|match") String task,
            @NotNull Boolean passed,
            @NotEmpty List<@Valid Check> checks) {
    }

    public record Check(@NotBlank @Size(max = 128) String name,
            @NotNull Boolean passed) {
    }
}
