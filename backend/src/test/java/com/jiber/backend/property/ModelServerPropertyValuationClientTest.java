package com.jiber.backend.property;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;

class ModelServerPropertyValuationClientTest {

    private MockRestServiceServer server;
    private ModelServerApartmentFeatureMapper featureMapper;

    @BeforeEach
    void setUp() {
        featureMapper = new ModelServerApartmentFeatureMapper();
    }

    @Test
    void mapsValuationSuccessToPublicResponse() {
        var client = newClient("");
        server.expect(requestTo("http://model.test/internal/v1/valuation/apartments"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.propertyId").value(1001))
                .andExpect(jsonPath("$.asOfDate").value("2026-06-12"))
                .andExpect(jsonPath("$.features.sido").value("서울특별시"))
                .andExpect(jsonPath("$.features.sigungu").value("강남구"))
                .andExpect(jsonPath("$.features.legalDong").value("예시동"))
                .andExpect(jsonPath("$.features.exclusiveAreaM2").value(84.95))
                .andExpect(jsonPath("$.features.floor").value(15))
                .andExpect(jsonPath("$.features.builtYear").value(2010))
                .andExpect(jsonPath("$.features.dealYear").value(2026))
                .andExpect(jsonPath("$.features.dealMonth").value(6))
                .andExpect(jsonPath("$.features.distanceToStationM").value(420))
                .andRespond(withSuccess("""
                        {
                          "supported": true,
                          "estimatedPrice": 1230000000,
                          "currency": "KRW",
                          "predictionInterval": {"lower": 1150000000, "upper": 1310000000},
                          "modelVersion": "hedonic-skeleton-v1",
                          "baselineDate": "2026-06-12",
                          "featureSetVersion": "apartment-basic-skeleton-v1",
                          "warnings": []
                        }
                        """, APPLICATION_JSON));

        var response = client.valuateApartment(1001L, valuationRequest());

        assertThat(response.propertyId()).isEqualTo(1001L);
        assertThat(response.supported()).isTrue();
        assertThat(response.estimatedPrice()).isEqualTo(1230000000L);
        assertThat(response.currency()).isEqualTo("KRW");
        assertThat(response.predictionInterval().lower()).isEqualTo(1150000000L);
        assertThat(response.predictionInterval().upper()).isEqualTo(1310000000L);
        assertThat(response.modelVersion()).isEqualTo("hedonic-skeleton-v1");
        assertThat(response.baselineDate()).isEqualTo(LocalDate.of(2026, 6, 12));
        assertThat(response.featureSetVersion()).isEqualTo("apartment-basic-skeleton-v1");
        assertThat(response.message()).isEqualTo("아파트 실거래 데이터를 바탕으로 계산한 추정가입니다.");
        server.verify();
    }

    @Test
    void mapsShapSuccessToPublicResponse() {
        var client = newClient("");
        server.expect(requestTo("http://model.test/internal/v1/shap/apartments"))
                .andExpect(method(POST))
                .andExpect(jsonPath("$.propertyId").value(1001))
                .andRespond(withSuccess("""
                        {
                          "supported": true,
                          "baseValue": 980000000,
                          "prediction": 1230000000,
                          "currency": "KRW",
                          "values": [
                            {
                              "feature": "exclusiveAreaM2",
                              "labelKo": "전용면적",
                              "value": 84.95,
                              "shapValue": 120000000,
                              "direction": "UP"
                            }
                          ],
                          "modelVersion": "hedonic-skeleton-v1",
                          "baselineDate": "2026-06-12",
                          "featureSetVersion": "apartment-basic-skeleton-v1"
                        }
                        """, APPLICATION_JSON));

        var response = client.explainApartment(1001L, shapRequest());

        assertThat(response.propertyId()).isEqualTo(1001L);
        assertThat(response.supported()).isTrue();
        assertThat(response.baseValue()).isEqualTo(980000000L);
        assertThat(response.prediction()).isEqualTo(1230000000L);
        assertThat(response.values()).singleElement().satisfies(value -> {
            assertThat(value.feature()).isEqualTo("exclusiveAreaM2");
            assertThat(value.labelKo()).isEqualTo("전용면적");
            assertThat(value.value()).isEqualByComparingTo("84.95");
            assertThat(value.shapValue()).isEqualTo(120000000L);
            assertThat(value.direction()).isEqualTo(ShapDirection.UP);
        });
        assertThat(response.message()).isEqualTo("추정가에 영향을 준 주요 요인입니다.");
        server.verify();
    }

    @Test
    void mapsInsufficientDataPayloadToPublicError() {
        var client = newClient("");
        server.expect(requestTo("http://model.test/internal/v1/valuation/apartments"))
                .andExpect(method(POST))
                .andRespond(withSuccess("""
                        {
                          "supported": false,
                          "reason": "INSUFFICIENT_DATA",
                          "missingFeatures": ["exclusiveAreaM2", "builtYear"],
                          "modelVersion": "hedonic-skeleton-v1",
                          "baselineDate": "2026-06-12",
                          "featureSetVersion": "apartment-basic-skeleton-v1"
                        }
                        """, APPLICATION_JSON));

        assertThatThrownBy(() -> client.valuateApartment(1001L, valuationRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALUATION_INSUFFICIENT_DATA);
                    assertThat(exception.getDetails())
                            .extracting("field")
                            .containsExactly("exclusiveAreaM2", "builtYear");
                });
        server.verify();
    }

    @Test
    void mapsServerErrorsToUnavailableError() {
        var client = newClient("");
        server.expect(requestTo("http://model.test/internal/v1/valuation/apartments"))
                .andExpect(method(POST))
                .andRespond(withServerError());

        assertThatThrownBy(() -> client.valuateApartment(1001L, valuationRequest()))
                .isInstanceOfSatisfying(ApiException.class, exception ->
                        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.MODEL_SERVER_UNAVAILABLE));
        server.verify();
    }

    @Test
    void sendsInternalTokenWhenConfigured() {
        var client = newClient("test-token");
        server.expect(requestTo("http://model.test/internal/v1/valuation/apartments"))
                .andExpect(method(POST))
                .andExpect(header(AUTHORIZATION, "Bearer test-token"))
                .andRespond(withSuccess("""
                        {
                          "supported": true,
                          "estimatedPrice": 1,
                          "currency": "KRW",
                          "predictionInterval": {"lower": 1, "upper": 1},
                          "modelVersion": "hedonic-skeleton-v1",
                          "baselineDate": "2026-06-12",
                          "featureSetVersion": "apartment-basic-skeleton-v1",
                          "warnings": []
                        }
                        """, APPLICATION_JSON));

        client.valuateApartment(1001L, valuationRequest());

        server.verify();
    }

    private ModelServerPropertyValuationClient newClient(String internalToken) {
        var builder = RestClient.builder().baseUrl("http://model.test");
        server = MockRestServiceServer.bindTo(builder).build();
        return new ModelServerPropertyValuationClient(builder.build(), internalToken, featureMapper);
    }

    private ValuationRequest valuationRequest() {
        return new ValuationRequest(new BigDecimal("84.95"), 15, LocalDate.of(2026, 6, 12));
    }

    private ShapRequest shapRequest() {
        return new ShapRequest(new BigDecimal("84.95"), 15, LocalDate.of(2026, 6, 12));
    }
}
