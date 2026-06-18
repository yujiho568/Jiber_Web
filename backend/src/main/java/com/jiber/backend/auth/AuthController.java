package com.jiber.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialLoginService socialLoginService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final PendingSocialCookieService pendingSocialCookieService;

    public AuthController(
            AuthService authService,
            SocialLoginService socialLoginService,
            RefreshTokenCookieService refreshTokenCookieService,
            PendingSocialCookieService pendingSocialCookieService
    ) {
        this.authService = authService;
        this.socialLoginService = socialLoginService;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.pendingSocialCookieService = pendingSocialCookieService;
    }

    @GetMapping("/me")
    public AuthMeResponse me(Authentication authentication) {
        return authService.currentUser(authentication);
    }

    @PostMapping("/signup")
    public AuthTokenResponse signup(
            @Valid @RequestBody EmailSignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = authService.signup(signupRequest, RefreshRequestContext.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString());
        return result.response();
    }

    @PostMapping("/login")
    public AuthTokenResponse login(
            @RequestBody EmailLoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = authService.login(loginRequest, RefreshRequestContext.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString());
        return result.response();
    }

    @GetMapping("/social/pending")
    public SocialPendingResponse socialPending(
            @CookieValue(name = "${jiber.auth.pending-social.cookie.name:JIBER_PENDING_SOCIAL}", required = false) String pendingToken
    ) {
        return socialLoginService.pending(pendingToken);
    }

    @PostMapping("/social/signup")
    public AuthTokenResponse socialSignup(
            @CookieValue(name = "${jiber.auth.pending-social.cookie.name:JIBER_PENDING_SOCIAL}", required = false) String pendingToken,
            @Valid @RequestBody SocialSignupRequest signupRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = socialLoginService.socialSignup(pendingToken, signupRequest, RefreshRequestContext.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, pendingSocialCookieService.clearPendingCookie().toString());
        return result.response();
    }

    @PostMapping("/social/link")
    public AuthTokenResponse socialLink(
            @CookieValue(name = "${jiber.auth.pending-social.cookie.name:JIBER_PENDING_SOCIAL}", required = false) String pendingToken,
            @Valid @RequestBody SocialLinkRequest linkRequest,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = socialLoginService.socialLink(pendingToken, linkRequest, RefreshRequestContext.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, pendingSocialCookieService.clearPendingCookie().toString());
        return result.response();
    }

    @PostMapping("/refresh")
    public AuthTokenResponse refresh(
            @CookieValue(name = "${jiber.auth.refresh-token.cookie.name:JIBER_REFRESH_TOKEN}", required = false) String refreshToken,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        var result = authService.refresh(refreshToken, RefreshRequestContext.from(request));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.createRefreshCookie(result.refreshToken()).toString());
        return result.response();
    }

    @PostMapping("/logout")
    public AuthLogoutResponse logout(
            @CookieValue(name = "${jiber.auth.refresh-token.cookie.name:JIBER_REFRESH_TOKEN}", required = false) String refreshToken,
            @RequestBody(required = false) AuthLogoutRequest logoutRequest,
            HttpServletResponse response
    ) {
        authService.logout(refreshToken);
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clearRefreshCookie().toString());
        return new AuthLogoutResponse("로그아웃되었습니다.");
    }
}
