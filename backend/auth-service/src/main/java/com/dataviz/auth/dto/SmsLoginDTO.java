package com.dataviz.auth.dto;

import com.dataviz.auth.constant.SmsTerminalConstant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信登录请求：手机号 + 验证码，<strong>不含口令</strong>。
 * <p>
 * 刻意不复用 {@link LoginDTO}——它的 username/password 都带 {@code @NotBlank}，
 * 借给它就等于要求短信登录也交出明文口令。
 * </p>
 */
@Data
public class SmsLoginDTO {

    /** 登录手机号，账号由它定位 */
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Phone format is invalid")
    private String phone;

    /** 短信验证码 */
    @NotBlank(message = "Sms code is required")
    @Pattern(regexp = "^\\d{4,8}$", message = "Sms code format is invalid")
    private String code;

    /**
     * 登录发起的终端，必须与发码时同一个值——码是按终端存的，传错只会得到"验证码已过期"。
     * 这不是宽松的兼容字段：换端复用同一个码本来就该失败。
     */
    @NotBlank(message = "Terminal is required")
    @Pattern(regexp = SmsTerminalConstant.PATTERN, message = "Terminal is not supported")
    private String terminal;
}
