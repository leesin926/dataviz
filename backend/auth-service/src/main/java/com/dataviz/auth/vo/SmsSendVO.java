package com.dataviz.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 验证码发送结果：把服务端的两个时间窗回给前端，避免倒计时数字在两处各写一份。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SmsSendVO {

    /** 验证码有效期（秒） */
    private Integer expireSeconds;

    /** 两次发送之间的最小间隔（秒），前端据此起倒计时 */
    private Integer resendAfterSeconds;
}
