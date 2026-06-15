package com.jiber.backend.property;

import java.time.LocalDate;

public record ValuationResponse(
        Long propertyId,
        boolean supported,
        Long estimatedPrice,
        String currency,
        PredictionIntervalResponse predictionInterval,
        String modelVersion,
        LocalDate baselineDate,
        String featureSetVersion,
        String message
) {
}
