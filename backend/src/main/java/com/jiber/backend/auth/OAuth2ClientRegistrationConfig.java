package com.jiber.backend.auth;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.util.StringUtils;

@Configuration
@EnableConfigurationProperties(OAuth2ClientRegistrationProperties.class)
public class OAuth2ClientRegistrationConfig {

    @Bean
    @Conditional(ConfiguredOAuth2ProviderCondition.class)
    ClientRegistrationRepository clientRegistrationRepository(OAuth2ClientRegistrationProperties properties) {
        return new InMemoryClientRegistrationRepository(properties.toClientRegistrations());
    }

    static class ConfiguredOAuth2ProviderCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            var environment = context.getEnvironment();
            return configured(
                    environment.getProperty("jiber.auth.oauth2.google.client-id"),
                    environment.getProperty("jiber.auth.oauth2.google.client-secret")
            ) || configured(
                    environment.getProperty("jiber.auth.oauth2.kakao.client-id"),
                    environment.getProperty("jiber.auth.oauth2.kakao.client-secret")
            ) || configured(
                    environment.getProperty("jiber.auth.oauth2.naver.client-id"),
                    environment.getProperty("jiber.auth.oauth2.naver.client-secret")
            );
        }

        private boolean configured(String clientId, String clientSecret) {
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
        }
    }
}
