package dev.llmevalhub.usage;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePriceRequest(
        @NotBlank @Size(max = 128) String modelId,
        @NotBlank @Size(max = 64) String version,
        @NotBlank @Pattern(regexp = "[A-Z]{3}") String currency,
        @NotNull @DecimalMin("0") BigDecimal inputPerMillion,
        @NotNull @DecimalMin("0") BigDecimal outputPerMillion) {
}
