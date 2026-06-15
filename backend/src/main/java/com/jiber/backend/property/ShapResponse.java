package com.jiber.backend.property;

import java.time.LocalDate;
import java.util.List;

public record ShapResponse(
        Long propertyId,
        boolean supported,
        Long baseValue,
        Long prediction,
        String currency,
        List<ShapValueResponse> values,
        String modelVersion,
        LocalDate baselineDate,
        String featureSetVersion,
        String message
) {
}
