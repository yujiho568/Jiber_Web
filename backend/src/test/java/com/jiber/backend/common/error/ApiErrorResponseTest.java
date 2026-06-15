package com.jiber.backend.common.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ApiErrorResponseTest {

    @Test
    void createsContractShapeWithStableFields() {
        var timestamp = OffsetDateTime.parse("2026-06-12T10:30:00+09:00");
        var detail = new ErrorDetail("propertyId", "존재하지 않는 ID입니다.");

        var response = ApiErrorResponse.of(
                ErrorCode.PROPERTY_NOT_FOUND,
                "요청한 부동산 정보를 찾을 수 없습니다.",
                List.of(detail),
                "/api/v1/properties/123",
                timestamp
        );

        assertThat(response.code()).isEqualTo("PROPERTY_NOT_FOUND");
        assertThat(response.message()).isEqualTo("요청한 부동산 정보를 찾을 수 없습니다.");
        assertThat(response.details()).containsExactly(detail);
        assertThat(response.path()).isEqualTo("/api/v1/properties/123");
        assertThat(response.timestamp()).isEqualTo(timestamp);
    }
}
