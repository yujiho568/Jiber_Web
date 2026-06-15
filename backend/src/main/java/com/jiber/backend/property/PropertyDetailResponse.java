package com.jiber.backend.property;

import java.time.LocalDate;
import java.util.List;

public record PropertyDetailResponse(
        Long propertyId,
        PropertyType propertyType,
        String name,
        Address address,
        Location location,
        Summary summary,
        List<PropertyTransactionResponse> transactions,
        FavoriteSummary favorite,
        AiMetadata ai
) {
    public record Address(
            String sido,
            String sigungu,
            String legalDong,
            String roadAddress
    ) {
    }

    public record Location(
            Double lat,
            Double lng
    ) {
    }

    public record Summary(
            Integer builtYear,
            Integer householdCount,
            Long latestDealAmount,
            LocalDate latestDealDate
    ) {
    }

    public record FavoriteSummary(
            boolean apartmentFavorited,
            boolean areaFavorited
    ) {
    }

    public record AiMetadata(
            boolean valuationAvailable,
            boolean shapAvailable,
            String unsupportedReason
    ) {
    }
}
