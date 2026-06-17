package com.jiber.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

class OAuth2AuthorizationEndpointGuardFilter extends OncePerRequestFilter {

    private static final Pattern OAUTH2_AUTHORIZATION_PATH =
            Pattern.compile("^/oauth2/authorization/([^/]+)$");

    private final SecurityErrorResponseWriter securityErrorResponseWriter;
    private final ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

    OAuth2AuthorizationEndpointGuardFilter(
            SecurityErrorResponseWriter securityErrorResponseWriter,
            ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository
    ) {
        this.securityErrorResponseWriter = securityErrorResponseWriter;
        this.clientRegistrationRepository = clientRegistrationRepository;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        var registrationId = oauth2AuthorizationRegistrationId(request);
        if (registrationId == null) {
            filterChain.doFilter(request, response);
            return;
        }

        var repository = clientRegistrationRepository.getIfAvailable();
        if (repository == null || !hasRegistration(repository, registrationId)) {
            securityErrorResponseWriter.writeUnauthorized(
                    request,
                    response,
                    new InsufficientAuthenticationException("OAuth provider is not configured")
            );
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String oauth2AuthorizationRegistrationId(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return null;
        }
        var path = request.getRequestURI();
        var contextPath = request.getContextPath();
        if (StringUtils.hasText(contextPath) && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        var matcher = OAUTH2_AUTHORIZATION_PATH.matcher(path);
        return matcher.matches() ? matcher.group(1) : null;
    }

    private boolean hasRegistration(ClientRegistrationRepository repository, String registrationId) {
        try {
            return repository.findByRegistrationId(registrationId) != null;
        } catch (RuntimeException exception) {
            if (!"InvalidClientRegistrationIdException".equals(exception.getClass().getSimpleName())) {
                throw exception;
            }
            return false;
        }
    }
}
