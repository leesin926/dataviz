package com.dataviz.auth.controller;

import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.service.AuthService;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.TokenVO;
import com.dataviz.common.core.result.R;
import com.dataviz.common.security.context.SecurityContextHolder;
import com.dataviz.common.security.model.LoginUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller handling login, logout, token refresh, and captcha operations.
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and authorization APIs")
public class AuthController {

    private final AuthService authService;

    /**
     * User login with username/password and captcha.
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with credentials and captcha")
    public R<LoginVO> login(@RequestBody @Valid LoginDTO loginDTO) {
        log.info("Login attempt for user: {}", loginDTO.getUsername());
        LoginVO loginVO = authService.login(loginDTO);
        return R.ok(loginVO);
    }

    /**
     * User logout - invalidate current token.
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidate the current access token")
    public R<Void> logout(@RequestHeader(value = "X-User-Id", required = false) String userId,
                                @RequestHeader(value = "Authorization", required = false) String token) {
        log.info("Logout request for user: {}", userId);
        authService.logout(userId, token);
        return R.ok();
    }

    /**
     * Refresh access token using refresh token.
     */
    @PostMapping("/refreshToken")
    @Operation(summary = "Refresh token", description = "Get new access token using refresh token")
    public R<TokenVO> refreshToken(@RequestBody @Valid RefreshTokenDTO refreshTokenDTO) {
        log.info("Token refresh request");
        TokenVO tokenVO = authService.refreshToken(refreshTokenDTO);
        return R.ok(tokenVO);
    }

    /**
     * Generate captcha image and return captcha key.
     */
    @GetMapping("/captcha")
    @Operation(summary = "Get captcha", description = "Generate a captcha image for login verification")
    public R<CaptchaVO> getCaptcha() {
        CaptchaVO captchaVO = authService.generateCaptcha();
        return R.ok(captchaVO);
    }

    /**
     * Get current authenticated user info (resolved from token by AuthInterceptor).
     */
    @GetMapping("/userinfo")
    @Operation(summary = "Get current user", description = "Return the login user resolved from the access token")
    public R<LoginUser> getUserInfo() {
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        if (loginUser == null) {
            return R.fail(401, "Not authenticated");
        }
        return R.ok(loginUser);
    }

    /**
     * SSO login endpoint for third-party authentication.
     */
    @PostMapping("/sso/login")
    @Operation(summary = "SSO login", description = "Single Sign-On login with third-party token")
    public R<LoginVO> ssoLogin(@RequestParam("ssoToken") String ssoToken,
                                     @RequestParam(value = "provider", defaultValue = "default") String provider) {
        log.info("SSO login attempt with provider: {}", provider);
        LoginVO loginVO = authService.ssoLogin(ssoToken, provider);
        return R.ok(loginVO);
    }

    /**
     * SSO callback endpoint.
     */
    @GetMapping("/sso/callback")
    @Operation(summary = "SSO callback", description = "SSO callback handler for OAuth flows")
    public R<LoginVO> ssoCallback(@RequestParam("code") String code,
                                        @RequestParam(value = "state", required = false) String state) {
        log.info("SSO callback received with code: {}", code);
        LoginVO loginVO = authService.ssoCallback(code, state);
        return R.ok(loginVO);
    }
}
