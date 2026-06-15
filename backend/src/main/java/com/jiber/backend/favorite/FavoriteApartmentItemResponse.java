package com.jiber.backend.favorite;

import com.jiber.backend.property.LatestTransactionResponse;
import com.jiber.backend.property.PropertyType;
import java.time.OffsetDateTime;

public record FavoriteApartmentItemResponse(
        Long favoriteId,
        Long propertyId,
        PropertyType propertyType,
        String name,
        String address,
        Double lat,
        Double lng,
        LatestTransactionResponse latestTransaction,
        OffsetDateTime createdAt
) {
}
