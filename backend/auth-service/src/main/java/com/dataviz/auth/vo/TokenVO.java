package com.dataviz.auth.vo;

import lombok.Data;

/**
 * Token information VO.
 */
@Data
public class TokenVO {

    /** Access token string */
    private String accessToken;

    /** Refresh token string */
    private String refreshToken;

    /** User ID extracted from token */
    private String userId;

    /** Username extracted from token */
    private String username;

    /** Token expiration timestamp (epoch seconds) */
    private Long expireTime;
}
