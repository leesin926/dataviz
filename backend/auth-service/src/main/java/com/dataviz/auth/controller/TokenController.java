package com.dataviz.auth.controller;

import com.dataviz.auth.service.AuthService;
import com.dataviz.auth.vo.TokenVO;
import com.dataviz.common.core.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * Token validation endpoint for the gateway.
 * <p>
 * The gateway calls this endpoint to validate tokens when needed.
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/token")
@RequiredArgsConstructor
@Tag(name = "Token Management", description = "Token validation and introspection APIs")
public class TokenController {

    private final AuthService authService;

    /**
     * Validate a JWT token and return token information.
     */
    @GetMapping("/validate")
    @Operation(summary = "Validate token", description = "Validate a JWT token and return user info")
    public R<TokenVO> validateToken(@RequestHeader("Authorization") String authHeader) {
        String token = authHeader.replace("Bearer ", "");
        TokenVO tokenVO = authService.validateAndGetTokenInfo(token);
        return R.ok(tokenVO);
    }

    /**
     * Check if a token is still valid (active).
     */
    @GetMapping("/check")
    @Operation(summary = "Check token", description = "Check if a token is active and not expired")
    public R<Boolean> checkToken(@RequestParam("token") String token) {
        boolean isValid = authService.isTokenValid(token);
        return R.ok(isValid);
    }

    /**
     * Revoke all tokens for a specific user (admin operation).
     */
    @DeleteMapping("/revoke/{userId}")
    @Operation(summary = "Revoke tokens", description = "Revoke all active tokens for a user")
    public R<Void> revokeUserTokens(@PathVariable("userId") String userId) {
        log.info("Revoking all tokens for user: {}", userId);
        authService.revokeUserTokens(userId);
        return R.ok();
    }
}
