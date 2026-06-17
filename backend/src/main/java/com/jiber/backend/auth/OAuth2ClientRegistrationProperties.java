package com.jiber.backend.auth;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "jiber.auth.oauth2")
public class OAuth2ClientRegistrationProperties {

    private Provider google = new Provider();
    private Provider kakao = new Provider();
    private Provider naver = new Provider();

    public OAuth2ClientRegistrationProperties() {
    }

    public OAuth2ClientRegistrationProperties(Provider google, Provider kakao, Provider naver) {
        this.google = google == null ? new Provider() : google;
        this.kakao = kakao == null ? new Provider() : kakao;
        this.naver = naver == null ? new Provider() : naver;
    }

    public Provider getGoogle() {
        return google;
    }

    public void setGoogle(Provider google) {
        this.google = google == null ? new Provider() : google;
    }

    public Provider getKakao() {
        return kakao;
    }

    public void setKakao(Provider kakao) {
        this.kakao = kakao == null ? new Provider() : kakao;
    }

    public Provider getNaver() {
        return naver;
    }

    public void setNaver(Provider naver) {
        this.naver = naver == null ? new Provider() : naver;
    }

    public List<ClientRegistration> toClientRegistrations() {
        var registrations = new ArrayList<ClientRegistration>();
        if (google.configured()) {
            registrations.add(googleRegistration());
        }
        if (kakao.configured()) {
            registrations.add(kakaoRegistration());
        }
        if (naver.configured()) {
            registrations.add(naverRegistration());
        }
        return registrations;
    }

    private ClientRegistration googleRegistration() {
        return CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(google.clientId)
                .clientSecret(google.clientSecret)
                .redirectUri(google.redirectUriOrDefault("http://localhost:8080/login/oauth2/code/google"))
                .build();
    }

    private ClientRegistration kakaoRegistration() {
        return ClientRegistration.withRegistrationId("kakao")
                .clientId(kakao.clientId)
                .clientSecret(kakao.clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(kakao.redirectUriOrDefault("http://localhost:8080/login/oauth2/code/kakao"))
                .scope("profile_nickname", "account_email")
                .authorizationUri("https://kauth.kakao.com/oauth/authorize")
                .tokenUri("https://kauth.kakao.com/oauth/token")
                .userInfoUri("https://kapi.kakao.com/v2/user/me")
                .userNameAttributeName("id")
                .clientName("Kakao")
                .build();
    }

    private ClientRegistration naverRegistration() {
        return ClientRegistration.withRegistrationId("naver")
                .clientId(naver.clientId)
                .clientSecret(naver.clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri(naver.redirectUriOrDefault("http://localhost:8080/login/oauth2/code/naver"))
                .scope("name", "email")
                .authorizationUri("https://nid.naver.com/oauth2.0/authorize")
                .tokenUri("https://nid.naver.com/oauth2.0/token")
                .userInfoUri("https://openapi.naver.com/v1/nid/me")
                .userNameAttributeName("response")
                .clientName("Naver")
                .build();
    }

    public static class Provider {

        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";

        public Provider() {
        }

        public Provider(String clientId, String clientSecret, String redirectUri) {
            this.clientId = clientId;
            this.clientSecret = clientSecret;
            this.redirectUri = redirectUri;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String clientId) {
            this.clientId = clientId;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String clientSecret) {
            this.clientSecret = clientSecret;
        }

        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(String redirectUri) {
            this.redirectUri = redirectUri;
        }

        boolean configured() {
            return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret);
        }

        String redirectUriOrDefault(String defaultValue) {
            return StringUtils.hasText(redirectUri) ? redirectUri : defaultValue;
        }
    }
}
