package com.dataviz.common.security.util;

import com.dataviz.common.security.model.LoginUser;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT工具类
 */
@Slf4j
public final class JwtHelper {

    private static final long DEFAULT_EXPIRATION = 24 * 60 * 60 * 1000; // 24小时

    private static String secretKey = "dataviz-secret-key-for-jwt-token-generation-must-be-long-enough-256bit";

    private JwtHelper() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * 生成Token
     */
    public static String createToken(LoginUser loginUser) {
        return createToken(loginUser, DEFAULT_EXPIRATION);
    }

    /**
     * 生成Token（自定义过期时间）
     */
    public static String createToken(LoginUser loginUser, long expiration) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", loginUser.getUserId());
        claims.put("username", loginUser.getUsername());
        claims.put("tenantId", loginUser.getTenantId());
        claims.put("deptId", loginUser.getDeptId());

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .claims(claims)
                .subject(loginUser.getUsername())
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 解析Token
     */
    public static Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.warn("Token已过期");
            throw e;
        } catch (JwtException e) {
            log.warn("Token无效: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * 从Token中获取用户ID
     */
    public static Long getUserId(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Token中获取用户名
     */
    public static String getUsername(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    /**
     * 从Token中获取租户ID
     */
    public static String getTenantId(String token) {
        Claims claims = parseToken(token);
        return claims.get("tenantId", String.class);
    }

    /**
     * 验证Token是否有效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    /**
     * 判断Token是否过期
     */
    public static boolean isTokenExpired(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException e) {
            return true;
        }
    }

    /**
     * 获取Token剩余有效期（秒）
     */
    public static long getRemainingExpiration(String token) {
        try {
            Claims claims = parseToken(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            return Math.max(remaining / 1000, 0);
        } catch (JwtException e) {
            return 0;
        }
    }

    /**
     * 刷新Token
     */
    public static String refreshToken(String oldToken) {
        Claims claims = parseToken(oldToken);
        Map<String, Object> newClaims = new HashMap<>();
        newClaims.put("userId", claims.get("userId"));
        newClaims.put("username", claims.getSubject());
        newClaims.put("tenantId", claims.get("tenantId"));
        newClaims.put("deptId", claims.get("deptId"));

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + DEFAULT_EXPIRATION);

        return Jwts.builder()
                .claims(newClaims)
                .subject(claims.getSubject())
                .issuedAt(now)
                .expiration(expireDate)
                .signWith(getSecretKey())
                .compact();
    }

    private static SecretKey getSecretKey() {
        byte[] keyBytes = Decoders.BASE64.decode(
            java.util.Base64.getEncoder().encodeToString(secretKey.getBytes())
        );
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
