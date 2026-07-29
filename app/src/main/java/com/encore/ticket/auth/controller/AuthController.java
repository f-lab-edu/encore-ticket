package com.encore.ticket.auth.controller;

import com.encore.ticket.auth.api.AuthProvider;
import com.encore.ticket.auth.api.dto.SocialLoginResponse;
import com.encore.ticket.auth.api.dto.TokenRefreshResponse;
import com.encore.ticket.auth.api.exception.InvalidRefreshTokenException;
import com.encore.ticket.auth.api.exception.UnsupportedProviderException;

import jakarta.servlet.http.Cookie;
import jakarta.validation.constraints.NotBlank;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/auth")
public class AuthController {


    private static final String TOKEN_TYPE = "Bearer";
    private static final String OAUTH_STATE_COOKIE = "oauthState";
    private static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    private static final long ACCESS_TOKEN_EXPIRES_IN_SECONDS = 1800L;

    private static final String STUB_OAUTH_STATE = "stub-state";

    @GetMapping("/oauth/{provider}/authorize")
    ResponseEntity<Void> authorize(@PathVariable("provider") String provider) {
        AuthProvider authProvider = toAuthProvider(provider);

        return ResponseEntity.status(HttpStatus.FOUND)
                .location(stubAuthorizationUri(authProvider))
                .header(HttpHeaders.SET_COOKIE, cookie(OAUTH_STATE_COOKIE, STUB_OAUTH_STATE).toString())
                .build();
    }

    @GetMapping("/oauth/{provider}/callback")
    ResponseEntity<SocialLoginResponse> callback(
            @PathVariable("provider") String provider,
            @RequestParam @NotBlank String code,
            @RequestParam @NotBlank String state) {

        SocialLoginResponse body = new SocialLoginResponse(
                "access-token",
                TOKEN_TYPE,
                ACCESS_TOKEN_EXPIRES_IN_SECONDS,
                new SocialLoginResponse.User(1L, "홍길동", toAuthProvider(provider), false));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, cookie(REFRESH_TOKEN_COOKIE, "refresh-token").toString())
                .body(body);
    }

    @PostMapping("/refresh")
    ResponseEntity<TokenRefreshResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) Cookie refreshToken) {
        if (refreshToken == null || refreshToken.getValue() == null || refreshToken.getValue().isBlank()) {
            throw new InvalidRefreshTokenException();
        }

        return ResponseEntity.ok(
                new TokenRefreshResponse("new-access-token", TOKEN_TYPE, ACCESS_TOKEN_EXPIRES_IN_SECONDS));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredCookie(REFRESH_TOKEN_COOKIE).toString())
                .build();
    }

    private static AuthProvider toAuthProvider(String provider) {
        return AuthProvider.from(provider)
                .orElseThrow(() -> new UnsupportedProviderException(provider));
    }

    private static URI stubAuthorizationUri(AuthProvider provider) {
        return UriComponentsBuilder.fromUriString(stubAuthorizationEndpoint(provider))
                .queryParam("client_id", "stub-client-id")
                .queryParam("redirect_uri", "stub-redirect-uri")
                .queryParam("state", STUB_OAUTH_STATE)
                .queryParam("scope", "stub-scope")
                .encode()
                .build()
                .toUri();
    }


    private static String stubAuthorizationEndpoint(AuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> "https://accounts.google.com/o/oauth2/v2/auth";
            case KAKAO -> "https://kauth.kakao.com/oauth/authorize";
        };
    }

    private static ResponseCookie.ResponseCookieBuilder cookieBuilder(String name, String value) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/");
    }

    private static ResponseCookie cookie(String name, String value) {
        return cookieBuilder(name, value).build();
    }

    private static ResponseCookie expiredCookie(String name) {
        return cookieBuilder(name, "").maxAge(0).build();
    }
}
