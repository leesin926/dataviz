package com.dataviz.auth.vo;

import lombok.Data;

import java.util.List;

/**
 * Login response VO containing tokens and user permissions.
 */
@Data
public class LoginVO {

    /** JWT access token */
    private String accessToken;

    /** Refresh token for obtaining new access tokens */
    private String refreshToken;

    /** Authenticated user ID */
    private Long userId;

    /** Authenticated username */
    private String username;

    /** User's role codes */
    private List<String> roles;

    /** User's permission codes */
    private List<String> permissions;
}
