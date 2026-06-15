package com.jiber.backend.property;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorDetail;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record PropertySearchRequest(
        String sido,
        String sigungu,
        String legalDong,
        String complexName,
        String keyword,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal centerLat,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal centerLng,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal swLat,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal swLng,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal neLat,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal neLng,
        List<PropertyType> propertyTypes,
        List<TransactionType> transactionTypes,
        @PositiveOrZero Long minDealAmount,
        @PositiveOrZero Long maxDealAmount,
        @PositiveOrZero BigDecimal minAreaM2,
        @PositiveOrZero BigDecimal maxAreaM2,
        @Min(1900) Integer dealYearFrom,
        @Min(1900) Integer dealYearTo,
        @PositiveOrZero Integer page,
        @Positive @Max(100) Integer size,
        String sort
) {
    public int effectivePage() {
        return page == null ? 0 : page;
    }

    public int effectiveSize() {
        return size == null ? 20 : size;
    }

    public void validateRanges() {
        var details = new ArrayList<ErrorDetail>();
        validateBoundsCompleteness(details);
        validateCenterPair(details);
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

    private void validateBoundsCompleteness(List<ErrorDetail> details) {
        var count = 0;
        count += swLat == null ? 0 : 1;
        count += swLng == null ? 0 : 1;
        count += neLat == null ? 0 : 1;
        count += neLng == null ? 0 : 1;
        if (count > 0 && count < 4) {
            details.add(new ErrorDetail("bounds", "bounds를 사용할 때는 swLat, swLng, neLat, neLng를 모두 보내야 합니다."));
        }
    }

    private void validateCenterPair(List<ErrorDetail> details) {
        if ((centerLat == null) != (centerLng == null)) {
            details.add(new ErrorDetail("center", "centerLat와 centerLng는 함께 보내야 합니다."));
        }
    }
}
