package com.jiber.backend.property;

public record PropertySearchItemResponse(
        Long propertyId,
        PropertyType propertyType,
        String name,
        String address,
        String legalDong,
        Double lat,
        Double lng,
        Integer distanceM,
        LatestTransactionResponse latestTransaction,
        boolean aiAvailable
) {
}
