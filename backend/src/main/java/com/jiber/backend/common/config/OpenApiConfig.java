package com.jiber.backend.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI jiberOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jiber Web API")
                        .version("v1")
                        .description("지도 기반 부동산 정보와 Phase 1 백엔드 API 스켈레톤입니다."));
    }
}
