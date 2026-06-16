package com.jiber.backend.property;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import com.jiber.backend.common.error.ErrorDetail;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ModelServerPropertyValuationClient implements PropertyValuationClient {

    private static final String VALUATION_PATH = "/internal/v1/valuation/apartments";
    private static final String SHAP_PATH = "/internal/v1/shap/apartments";
    private static final String INSUFFICIENT_DATA = "INSUFFICIENT_DATA";

    private final RestClient restClient;
    private final String internalToken;
    private final ModelServerApartmentFeatureMapper featureMapper;

    public ModelServerPropertyValuationClient(
            RestClient.Builder restClientBuilder,
            ModelServerApartmentFeatureMapper featureMapper,
            @Value("${jiber.model-server.base-url:http://localhost:8000}") String baseUrl,
            @Value("${jiber.model-server.internal-token:}") String internalToken,
            @Value("${jiber.model-server.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${jiber.model-server.read-timeout-ms:5000}") int readTimeoutMs
    ) {
        this(
                restClientBuilder
                        .baseUrl(removeTrailingSlash(baseUrl))
                        .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                        .build(),
                internalToken,
                featureMapper
        );
    }

    ModelServerPropertyValuationClient(
            RestClient restClient,
            String internalToken,
            ModelServerApartmentFeatureMapper featureMapper
    ) {
        this.restClient = restClient;
        this.internalToken = internalToken == null ? "" : internalToken.trim();
        this.featureMapper = featureMapper;
    }

    @Override
    public ValuationResponse valuateApartment(Long propertyId, ValuationRequest request) {
        var internalRequest = featureMapper.toInternalRequest(propertyId, request);
        var response = post(VALUATION_PATH, internalRequest, ModelServerValuationResponse.class);
        ensureSupported(response.supported(), response.reason(), response.missingFeatures());
        return new ValuationResponse(
                propertyId,
                true,
                response.estimatedPrice(),
                response.currency(),
                new PredictionIntervalResponse(response.predictionInterval().lower(), response.predictionInterval().upper()),
                response.modelVersion(),
                LocalDate.parse(response.baselineDate()),
                response.featureSetVersion(),
                "아파트 실거래 데이터를 바탕으로 계산한 추정가입니다."
        );
    }

    @Override
    public ShapResponse explainApartment(Long propertyId, ShapRequest request) {
        var internalRequest = featureMapper.toInternalRequest(propertyId, request);
        var response = post(SHAP_PATH, internalRequest, ModelServerShapResponse.class);
        ensureSupported(response.supported(), response.reason(), response.missingFeatures());
        return new ShapResponse(
                propertyId,
                true,
                response.baseValue(),
                response.prediction(),
                response.currency(),
                response.values().stream()
                        .map(value -> new ShapValueResponse(
                                value.feature(),
                                value.labelKo(),
                                BigDecimal.valueOf(value.value()),
                                value.shapValue(),
                                ShapDirection.valueOf(value.direction())
                        ))
                        .toList(),
                response.modelVersion(),
                LocalDate.parse(response.baselineDate()),
                response.featureSetVersion(),
                "추정가에 영향을 준 주요 요인입니다."
        );
    }

    private <T> T post(String path, ModelServerApartmentInferenceRequest request, Class<T> responseType) {
        try {
            var spec = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request);
            if (StringUtils.hasText(internalToken)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken);
            }
            var response = spec.retrieve().body(responseType);
            if (response == null) {
                throw new ApiException(ErrorCode.MODEL_SERVER_UNAVAILABLE);
            }
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.MODEL_SERVER_UNAVAILABLE);
        }
    }

    private void ensureSupported(Boolean supported, String reason, List<String> missingFeatures) {
        if (Boolean.TRUE.equals(supported)) {
            return;
        }
        if (INSUFFICIENT_DATA.equals(reason)) {
            var details = missingFeatures == null ? List.<ErrorDetail>of() : missingFeatures.stream()
                    .map(feature -> new ErrorDetail(feature, "모델 서버 추론에 필요한 feature입니다."))
                    .toList();
            throw new ApiException(ErrorCode.VALUATION_INSUFFICIENT_DATA, ErrorCode.VALUATION_INSUFFICIENT_DATA.defaultMessage(), details);
        }
        throw new ApiException(ErrorCode.MODEL_SERVER_UNAVAILABLE);
    }

    private static String removeTrailingSlash(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "http://localhost:8000";
        }
        var trimmed = baseUrl.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static SimpleClientHttpRequestFactory requestFactory(int connectTimeoutMs, int readTimeoutMs) {
        var requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeoutMs);
        requestFactory.setReadTimeout(readTimeoutMs);
        return requestFactory;
    }
}
