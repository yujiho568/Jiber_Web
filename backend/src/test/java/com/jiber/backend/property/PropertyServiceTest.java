package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PropertyServiceTest {

    @Test
    void rejectsNonApartmentBeforeCallingModelServer() {
        var modelClientCalled = new AtomicBoolean(false);
        var modelClient = new PropertyValuationClient() {
            @Override
            public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
                modelClientCalled.set(true);
                throw new AssertionError("model-server must not be called for non-apartment properties");
            }

            @Override
            public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
                modelClientCalled.set(true);
                throw new AssertionError("model-server must not be called for non-apartment properties");
            }
        };
        var service = new PropertyService(
                new PropertyAiEligibilityService(),
                modelClient,
                propertyId -> PropertyType.OFFICETEL
        );

        assertThatThrownBy(() -> service.valuateApartment(1001L, valuationRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALUATION_UNSUPPORTED_PROPERTY_TYPE));
        assertThat(modelClientCalled).isFalse();
    }

    private ValuationRequest valuationRequest() {
        return new ValuationRequest(new BigDecimal("84.95"), 15, LocalDate.of(2026, 6, 12));
    }
}
