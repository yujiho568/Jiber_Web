package com.jiber.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

class OAuth2ClientRegistrationConfigTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(OAuth2ClientRegistrationConfig.class);

    @Test
    void emptyOAuthEnvironmentDoesNotCreateClientRegistrationRepository() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(ClientRegistrationRepository.class);
        });
    }

    @Test
    void registersOnlyConfiguredProvider() {
        contextRunner
                .withPropertyValues(
                        "jiber.auth.oauth2.kakao.client-id=dummy-kakao-client-id",
                        "jiber.auth.oauth2.kakao.client-secret=dummy-kakao-client-secret",
                        "jiber.auth.oauth2.kakao.redirect-uri=http://localhost:8080/login/oauth2/code/kakao"
                )
                .run(context -> {
                    var repository = context.getBean(ClientRegistrationRepository.class);

                    assertThat(repository.findByRegistrationId("kakao")).isNotNull();
                    assertThat(repository.findByRegistrationId("google")).isNull();
                    assertThat(repository.findByRegistrationId("naver")).isNull();
                });
    }

    @Test
    void kakaoAndNaverMetadataMatchProviderContracts() {
        contextRunner
                .withPropertyValues(
                        "jiber.auth.oauth2.kakao.client-id=dummy-kakao-client-id",
                        "jiber.auth.oauth2.kakao.client-secret=dummy-kakao-client-secret",
                        "jiber.auth.oauth2.kakao.redirect-uri=http://localhost:8080/login/oauth2/code/kakao",
                        "jiber.auth.oauth2.naver.client-id=dummy-naver-client-id",
                        "jiber.auth.oauth2.naver.client-secret=dummy-naver-client-secret",
                        "jiber.auth.oauth2.naver.redirect-uri=http://localhost:8080/login/oauth2/code/naver"
                )
                .run(context -> {
                    var repository = context.getBean(ClientRegistrationRepository.class);
                    var kakao = repository.findByRegistrationId("kakao");
                    var naver = repository.findByRegistrationId("naver");

                    assertThat(kakao.getProviderDetails().getAuthorizationUri()).isEqualTo("https://kauth.kakao.com/oauth/authorize");
                    assertThat(kakao.getProviderDetails().getTokenUri()).isEqualTo("https://kauth.kakao.com/oauth/token");
                    assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://kapi.kakao.com/v2/user/me");
                    assertThat(kakao.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("id");
                    assertThat(kakao.getScopes()).containsExactlyInAnyOrder("profile_nickname", "account_email");

                    assertThat(naver.getProviderDetails().getAuthorizationUri()).isEqualTo("https://nid.naver.com/oauth2.0/authorize");
                    assertThat(naver.getProviderDetails().getTokenUri()).isEqualTo("https://nid.naver.com/oauth2.0/token");
                    assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUri()).isEqualTo("https://openapi.naver.com/v1/nid/me");
                    assertThat(naver.getProviderDetails().getUserInfoEndpoint().getUserNameAttributeName()).isEqualTo("response");
                    assertThat(naver.getScopes()).containsExactlyInAnyOrder("name", "email");
                });
    }
}
