package com.jiber.backend.property;

public record ModelServerApartmentInferenceRequest(
        Long propertyId,
        String asOfDate,
        ModelServerApartmentFeatures features
) {
}
