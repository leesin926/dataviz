package com.dataviz.auth.service;

import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.dto.SmsLoginDTO;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.SmsSendVO;
import com.dataviz.auth.vo.TokenVO;
import com.dataviz.common.security.model.LoginUser;

/**
 * Authentication service interface.
 */
public interface AuthService {

    /**
     * Authenticate user and return login response with tokens and permissions.
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * Logout and invalidate the user's current token.
     *
     * @param currentUser   服务端解析出的登录主体（来自会话快照，不是调用方声明的头）；为 null 时只拉黑 token
     * @param authorization 原始 {@code Authorization} 头，允许带或带 "Bearer " 前缀
     */
    void logout(LoginUser currentUser, String authorization);

    /**
     * Refresh the access token using a valid refresh token.
     */
    TokenVO refreshToken(RefreshTokenDTO refreshTokenDTO);

    /**
     * Generate a captcha image.
     */
    CaptchaVO generateCaptcha();

    /**
     * 下发短信验证码（状态机见 {@link SmsCodeService}；未接入短信渠道，码值为配置常量）。
     *
     * @param terminal 登录发起端，用于把各端的闸门与码值隔开
     */
    SmsSendVO sendSmsCode(String phone, String terminal);

    /**
     * 短信登录：验证码校验通过后，走与密码登录<strong>同一条</strong>发 token / 写会话快照 / 记登录日志的路径。
     */
    LoginVO smsLogin(SmsLoginDTO smsLoginDTO);

    /**
     * SSO login with third-party token.
     */
    LoginVO ssoLogin(String ssoToken, String provider);

    /**
     * SSO callback handler.
     */
    LoginVO ssoCallback(String code, String state);

    /**
     * Validate a token and return token information.
     */
    TokenVO validateAndGetTokenInfo(String token);

    /**
     * Check if a token is valid.
     */
    boolean isTokenValid(String token);

    /**
     * Revoke all tokens for a user.
     */
    void revokeUserTokens(String userId);
}
