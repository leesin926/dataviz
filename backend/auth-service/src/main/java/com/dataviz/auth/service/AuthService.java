package com.dataviz.auth.service;

import com.dataviz.auth.dto.LoginDTO;
import com.dataviz.auth.dto.RefreshTokenDTO;
import com.dataviz.auth.vo.CaptchaVO;
import com.dataviz.auth.vo.LoginVO;
import com.dataviz.auth.vo.TokenVO;

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
     */
    void logout(String userId, String token);

    /**
     * Refresh the access token using a valid refresh token.
     */
    TokenVO refreshToken(RefreshTokenDTO refreshTokenDTO);

    /**
     * Generate a captcha image.
     */
    CaptchaVO generateCaptcha();

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
