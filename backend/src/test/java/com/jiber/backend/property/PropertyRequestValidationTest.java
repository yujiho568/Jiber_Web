package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class PropertyRequestValidationTest {

    @Test
    void mapRequestRejectsInvalidBoundsRange() {
        var request = new MapSearchRequest(
                new BigDecimal("37.60"),
                new BigDecimal("127.20"),
                new BigDecimal("37.40"),
                new BigDecimal("126.90"),
                5,
                List.of(PropertyType.APARTMENT),
                List.of(TransactionType.SALE),
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertThatThrownBy(request::validateRanges)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getDetails()).extracting("field").contains("swLat", "swLng");
                });
    }

    @Test
    void searchRequestRejectsIncompleteBoundsAndCenterPair() {
        var request = new PropertySearchRequest(
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("37.50"),
                null,
                new BigDecimal("37.40"),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                20,
                null
        );

        assertThatThrownBy(request::validateRanges)
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
                    assertThat(exception.getDetails()).extracting("field").contains("bounds", "center");
                });
    }
}
