package com.jiber.backend.security;

import static org.springframework.security.config.Customizer.withDefaults;

import com.jiber.backend.auth.JwtAuthenticationFilter;
import com.jiber.backend.auth.OAuth2LoginSuccessHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.AbstractAuthenticationFilterConfigurer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain apiSecurityFilterChain(
            HttpSecurity http,
            SecurityErrorResponseWriter securityErrorResponseWriter,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            ObjectProvider<OAuth2LoginSuccessHandler> oauth2LoginSuccessHandler,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository
    ) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/oauth2/**", "/login/oauth2/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/me").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/auth/social/pending").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/signup", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/social/signup", "/api/v1/auth/social/link").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/chat/real-estate").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/properties/map",
                                "/api/v1/properties/search",
                                "/api/v1/properties/*",
                                "/api/v1/notices",
                                "/api/v1/notices/*").permitAll()
                        .requestMatchers("/api/v1/admin/notices/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/favorites/**").hasAnyRole("USER", "ADMIN")
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/properties/*/valuation",
                                "/api/v1/properties/*/shap").hasAnyRole("USER", "ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(securityErrorResponseWriter::writeUnauthorized)
                        .accessDeniedHandler(securityErrorResponseWriter::writeAccessDenied))
                .addFilterBefore(
                        new OAuth2AuthorizationEndpointGuardFilter(
                                securityErrorResponseWriter,
                                clientRegistrationRepository
                        ),
                        OAuth2AuthorizationRequestRedirectFilter.class
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        var successHandler = oauth2LoginSuccessHandler.getIfAvailable();
        if (clientRegistrationRepository.getIfAvailable() != null && successHandler != null) {
            http.oauth2Login(oauth2 -> oauth2.successHandler(successHandler));
        } else {
            http.oauth2Login(AbstractAuthenticationFilterConfigurer::disable);
        }

        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
