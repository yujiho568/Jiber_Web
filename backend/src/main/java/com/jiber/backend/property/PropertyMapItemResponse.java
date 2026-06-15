package com.jiber.backend.property;

public record PropertyMapItemResponse(
        Long propertyId,
        PropertyType propertyType,
        String name,
        String address,
        Double lat,
        Double lng,
        LatestTransactionResponse latestTransaction,
        Integer dealCount,
        boolean aiAvailable
) {
}
