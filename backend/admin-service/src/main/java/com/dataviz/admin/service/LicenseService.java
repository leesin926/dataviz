package com.dataviz.admin.service;

import com.dataviz.admin.vo.LicenseVO;

public interface LicenseService {

    /** 当前许可证状态（不校验签名，只读库里已激活的那张 + 按今天算有效期） */
    LicenseVO current();

    /** 验签并激活；旧的一张转为历史 */
    LicenseVO activate(String licenseKey);
}
