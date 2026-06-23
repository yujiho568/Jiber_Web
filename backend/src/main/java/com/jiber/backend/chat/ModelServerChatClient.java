package com.jiber.backend.chat;

import com.jiber.backend.common.error.ApiException;
import com.jiber.backend.common.error.ErrorCode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class ModelServerChatClient {

    private static final String RETRIEVE_PATH = "/internal/v1/chat/real-estate/retrieve";

    private final RestClient restClient;
    private final String internalToken;

    @Autowired
    public ModelServerChatClient(
            RestClient.Builder restClientBuilder,
            @Value("${jiber.model-server.base-url:http://localhost:8000}") String baseUrl,
            @Value("${jiber.model-server.internal-token:}") String internalToken,
            @Value("${jiber.model-server.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${jiber.model-server.read-timeout-ms:120000}") int readTimeoutMs
    ) {
        this.restClient = restClientBuilder
                .baseUrl(removeTrailingSlash(baseUrl))
                .requestFactory(requestFactory(connectTimeoutMs, readTimeoutMs))
                .build();
        this.internalToken = internalToken == null ? "" : internalToken.trim();
    }

    public ChatRetrievalResponse retrieve(ChatRequest request) {
        try {
            var spec = restClient.post()
                    .uri(RETRIEVE_PATH)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request);
            if (StringUtils.hasText(internalToken)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + internalToken);
            }
            var response = spec.retrieve().body(ChatRetrievalResponse.class);
            if (response == null) {
                throw new ApiException(ErrorCode.CHATBOT_UNAVAILABLE);
            }
            return response;
        } catch (ApiException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new ApiException(ErrorCode.CHATBOT_UNAVAILABLE);
        }
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
