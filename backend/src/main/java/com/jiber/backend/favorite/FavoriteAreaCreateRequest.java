package com.jiber.backend.favorite;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record FavoriteAreaCreateRequest(
        @NotBlank @Size(max = 120) String label,
        @Size(max = 100) String sido,
        @Size(max = 100) String sigungu,
        @Size(max = 100) String legalDong,
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal centerLat,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal centerLng,
        @Min(1) @Max(14) Integer zoomLevel
) {
    @JsonIgnore
    @AssertTrue(message = "행정구역 또는 중심 좌표 중 하나는 필요합니다.")
    public boolean isAreaOrCoordinatesPresent() {
        return hasText(sido) || hasText(sigungu) || hasText(legalDong) || (centerLat != null && centerLng != null);
    }

    @JsonIgnore
    @AssertTrue(message = "centerLat와 centerLng는 함께 보내야 합니다.")
    public boolean isCoordinatePairValid() {
        return (centerLat == null && centerLng == null) || (centerLat != null && centerLng != null);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
