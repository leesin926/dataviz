package com.dataviz.auth.controller;

import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.dto.SmsLoginDTO;
import com.dataviz.auth.dto.SmsSendDTO;
import com.dataviz.auth.service.AuthService;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.SmsSendVO;
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
     * <p>
     * 登出者身份<strong>只从已认证的会话快照取</strong>，不再收 {@code X-User-Id} 请求头。
     * 本路径在网关是免登白名单，在 auth-service 侧又不在 {@code EXCLUDE_PATHS} 里（AuthInterceptor 会拦），
     * 所以到这里的请求一定带过合法 token —— 身份没必要、也不应该由调用方声明（见 API-31②）。
     */
    @PostMapping("/logout")
    @Operation(summary = "User logout", description = "Invalidate the current access token")
    public R<Void> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        LoginUser loginUser = SecurityContextHolder.getLoginUser();
        log.info("Logout request for user: {}", loginUser == null ? null : loginUser.getUserId());
        authService.logout(loginUser, authorization);
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
     * 下发短信验证码 —— 免登端点（网关与服务侧各有一条白名单，缺一即 401）。
     */
    @PostMapping("/sms/send")
    @Operation(summary = "Send SMS code", description = "Issue an SMS verification code for a bound phone number")
    public R<SmsSendVO> sendSmsCode(@RequestBody @Valid SmsSendDTO smsSendDTO) {
        log.info("短信验证码下发请求: phone={}, terminal={}", maskPhone(smsSendDTO.getPhone()), smsSendDTO.getTerminal());
        return R.ok(authService.sendSmsCode(smsSendDTO.getPhone(), smsSendDTO.getTerminal()));
    }

    /**
     * 短信登录 —— 返回体与密码登录同构（同一个 {@code LoginVO}），前端两条路可以共用一套会话落盘。
     */
    @PostMapping("/sms/login")
    @Operation(summary = "SMS login", description = "Authenticate with a phone number and its SMS verification code")
    public R<LoginVO> smsLogin(@RequestBody @Valid SmsLoginDTO smsLoginDTO) {
        log.info("短信登录请求: phone={}, terminal={}", maskPhone(smsLoginDTO.getPhone()), smsLoginDTO.getTerminal());
        return R.ok(authService.smsLogin(smsLoginDTO));
    }

    /** 手机号是准身份标识，日志里只留前后段。 */
    private static String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "***";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
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
