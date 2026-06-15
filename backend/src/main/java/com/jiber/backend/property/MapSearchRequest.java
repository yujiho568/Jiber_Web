package com.jiber.backend.property;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorDetail;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record MapSearchRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal swLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal swLng,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal neLat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal neLng,
        @NotNull @Min(1) @Max(14) Integer zoomLevel,
        List<PropertyType> propertyTypes,
        List<TransactionType> transactionTypes,
        @PositiveOrZero Long minDealAmount,
        @PositiveOrZero Long maxDealAmount,
        @PositiveOrZero BigDecimal minAreaM2,
        @PositiveOrZero BigDecimal maxAreaM2,
        @Min(1900) Integer dealYearFrom,
        @Min(1900) Integer dealYearTo
) {
    public void validateRanges() {
        var details = new ArrayList<ErrorDetail>();
        if (swLat != null && neLat != null && swLat.compareTo(neLat) >= 0) {
            details.add(new ErrorDetail("swLat", "swLat는 neLat보다 작아야 합니다."));
        }
        if (swLng != null && neLng != null && swLng.compareTo(neLng) >= 0) {
            details.add(new ErrorDetail("swLng", "swLng는 neLng보다 작아야 합니다."));
        }
        if (minDealAmount != null && maxDealAmount != null && minDealAmount > maxDealAmount) {
            details.add(new ErrorDetail("minDealAmount", "minDealAmount는 maxDealAmount보다 작거나 같아야 합니다."));
        }
        if (minAreaM2 != null && maxAreaM2 != null && minAreaM2.compareTo(maxAreaM2) > 0) {
            details.add(new ErrorDetail("minAreaM2", "minAreaM2는 maxAreaM2보다 작거나 같아야 합니다."));
        }
        if (dealYearFrom != null && dealYearTo != null && dealYearFrom > dealYearTo) {
            details.add(new ErrorDetail("dealYearFrom", "dealYearFrom은 dealYearTo보다 작거나 같아야 합니다."));
        }
        if (!details.isEmpty()) {
            throw ApiException.validation(details);
        }
    }
}
