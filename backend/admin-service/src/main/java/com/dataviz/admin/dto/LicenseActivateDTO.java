package com.dataviz.admin.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
import lombok.Data;

/**
 * 激活许可证。密钥只在请求体里出现一次，服务端只落指纹，不回显、不写日志。
 */
@Data
public class LicenseActivateDTO {

    @NotBlank(message = "许可证密钥不能为空")
    @Size(max = 1024, message = "许可证密钥过长")
    private String licenseKey;
}
