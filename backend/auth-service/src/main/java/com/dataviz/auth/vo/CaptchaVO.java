package com.dataviz.auth.vo;

import lombok.Data;

/**
 * Captcha response VO.
 */
@Data
public class CaptchaVO {

    /** Unique key to identify this captcha (used in login request) */
    private String captchaKey;

    /** Base64-encoded captcha image */
    private String captchaImage;

    /** Captcha expiration in seconds */
    private Integer expireSeconds;
}
