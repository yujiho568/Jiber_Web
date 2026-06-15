package com.jiber.backend.property;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

public record ShapRequest(
        @NotNull @DecimalMin("0.01") BigDecimal exclusiveAreaM2,
        @NotNull Integer floor,
        @NotNull LocalDate asOfDate
) {
}
