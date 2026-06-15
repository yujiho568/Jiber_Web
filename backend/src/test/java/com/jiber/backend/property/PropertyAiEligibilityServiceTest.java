package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PropertyAiEligibilityServiceTest {

    private final PropertyAiEligibilityService service = new PropertyAiEligibilityService();

    @Test
    void allowsApartmentValuationAndShapRequests() {
        assertThat(service.ensureApartmentSupported(PropertyType.APARTMENT)).isEqualTo(PropertyType.APARTMENT);
    }

    @Test
    void rejectsNonApartmentValuationAndShapRequestsWithContractCode() {
        assertThatThrownBy(() -> service.ensureApartmentSupported(PropertyType.OFFICETEL))
                .isInstanceOf(ApiException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE);
    }
}
