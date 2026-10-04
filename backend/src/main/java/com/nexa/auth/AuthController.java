package com.nexa.auth;

import com.nexa.auth.dto.AcceptInvitationRequest;
import com.nexa.auth.dto.AuthResponse;
import com.nexa.auth.dto.InvitationPreviewResponse;
import com.nexa.auth.dto.InvitationTokenRequest;
import com.nexa.auth.dto.LoginRequest;
import com.nexa.auth.dto.PasswordResetConfirmRequest;
import com.nexa.auth.dto.PasswordResetRequest;
import com.nexa.auth.dto.RegisterRequest;
import com.nexa.common.api.ApiError;
import com.nexa.common.api.ErrorCode;
import com.nexa.common.exception.UnauthorizedException;
import com.nexa.common.web.CorrelationIdFilter;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService, RefreshCookies refreshCookies) {
        this.authService = authService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return withSession(HttpStatus.CREATED, authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return withSession(HttpStatus.OK, authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            return withSession(HttpStatus.OK, authService.refresh(readRefreshCookie(request)));
        } catch (UnauthorizedException e) {
            // Also clear the dead cookie so the browser stops sending it.
            ApiError body = ApiError.of(ErrorCode.UNAUTHORIZED, e.getMessage(), request.getRequestURI(),
                    MDC.get(CorrelationIdFilter.MDC_KEY));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                    .body(body);
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<Void> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request) {
        authService.requestPasswordReset(request.email());
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        authService.confirmPasswordReset(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/invitations/preview")
    public InvitationPreviewResponse previewInvitation(@Valid @RequestBody InvitationTokenRequest request) {
        return authService.previewInvitation(request.token());
    }

    @PostMapping("/invitations/accept")
    public ResponseEntity<AuthResponse> acceptInvitation(@Valid @RequestBody AcceptInvitationRequest request) {
        return withSession(HttpStatus.CREATED, authService.acceptInvitation(request));
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthSession session) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE, refreshCookies.create(session.refreshToken()).toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(session.response());
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> refreshCookies.name().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
