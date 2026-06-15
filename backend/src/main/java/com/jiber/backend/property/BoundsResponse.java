package com.jiber.backend.property;

import java.math.BigDecimal;

public record BoundsResponse(
        BigDecimal swLat,
        BigDecimal swLng,
        BigDecimal neLat,
        BigDecimal neLng
) {
}
