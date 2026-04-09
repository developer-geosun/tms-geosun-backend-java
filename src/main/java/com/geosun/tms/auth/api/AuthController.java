package com.geosun.tms.auth.api;

import com.geosun.tms.auth.dto.request.LoginRequest;
import com.geosun.tms.auth.dto.request.RefreshRequest;
import com.geosun.tms.auth.dto.request.RegisterRequest;
import com.geosun.tms.auth.dto.request.ResendVerificationRequest;
import com.geosun.tms.auth.dto.request.VerifyEmailRequest;
import com.geosun.tms.auth.dto.response.AuthTokensResponse;
import com.geosun.tms.auth.dto.response.LogoutResponse;
import com.geosun.tms.auth.dto.response.OperationSuccessResponse;
import com.geosun.tms.auth.dto.response.RegisterResponse;
import com.geosun.tms.auth.dto.response.UserPublicDto;
import com.geosun.tms.auth.infrastructure.web.ClientIpResolver;
import com.geosun.tms.auth.security.UserPrincipal;
import com.geosun.tms.auth.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Публічні та захищені auth endpoint (префікс /api/v1/auth).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final ClientIpResolver clientIpResolver;

    public AuthController(AuthService authService, ClientIpResolver clientIpResolver) {
        this.authService = authService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping("/register")
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest request,
                                                     HttpServletRequest httpRequest) {
        RegisterResponse body = authService.register(request, clientIpResolver.resolve(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(body);
    }

    @PostMapping("/login")
    public AuthTokensResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, clientIpResolver.resolve(httpRequest));
    }

    @PostMapping("/verify-email")
    public OperationSuccessResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return authService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public OperationSuccessResponse resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        return authService.resendVerification(request);
    }

    @PostMapping("/refresh")
    public AuthTokensResponse refresh(@Valid @RequestBody RefreshRequest request, HttpServletRequest httpRequest) {
        return authService.refresh(request, clientIpResolver.resolve(httpRequest));
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.logout(principal);
    }

    @GetMapping("/me")
    public UserPublicDto me(@AuthenticationPrincipal UserPrincipal principal) {
        return authService.me(principal);
    }
}
