package com.jiber.backend.property;

import java.math.BigDecimal;

public record ShapValueResponse(
        String feature,
        String labelKo,
        BigDecimal value,
        Long shapValue,
        ShapDirection direction
) {
}
