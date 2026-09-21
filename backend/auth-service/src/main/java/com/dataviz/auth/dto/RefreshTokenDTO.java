package com.dataviz.auth.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Token refresh request DTO.
 */
@Data
public class RefreshTokenDTO {

    /** The refresh token to exchange for a new access token */
    @NotBlank(message = "Refresh token is required")
    private String refreshToken;
}
