package com.dataviz.auth.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Login request DTO.
 */
@Data
public class LoginDTO {

    /** Login username */
    @NotBlank(message = "Username is required")
    private String username;

    /** Login password */
    @NotBlank(message = "Password is required")
    private String password;

    /** Captcha verification key (from Redis) */
    private String captchaKey;

    /** Captcha code entered by user */
    private String captchaCode;
}
