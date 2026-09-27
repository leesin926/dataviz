package com.dataviz.auth.dto;

import com.dataviz.auth.constant.SmsTerminalConstant;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import lombok.Data;

/**
 * 短信验证码发送请求。
 */
@Data
public class SmsSendDTO {

    /** 接收验证码的手机号 */
    @NotBlank(message = "Phone is required")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "Phone format is invalid")
    private String phone;

    /**
     * 发起登录的终端，见 {@link SmsTerminalConstant}。
     * 必填：闸门与码值都按"手机号 + 终端"隔离，缺了它就等于把两个端重新并回同一条闸门。
     */
    @NotBlank(message = "Terminal is required")
    @Pattern(regexp = SmsTerminalConstant.PATTERN, message = "Terminal is not supported")
    private String terminal;
}
