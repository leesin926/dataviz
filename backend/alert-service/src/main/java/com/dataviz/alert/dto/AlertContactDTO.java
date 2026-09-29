package com.dataviz.alert.dto;

import lombok.Data;

/**
 * 联系人入参（新增与更新共用一个形状，更新必须带 id）。
 * <p>
 * {@code email} / {@code mobile} <b>至少填一个</b>：这条判据在服务层，不在库上加 CHECK ——
 * 项目要同时跑在 MySQL 5.7（CHECK 会被静默忽略）与 8.0 上，加了一致性也保不住，
 * 反而留下"库塞得进去、界面报错"的分叉。
 */
@Data
public class AlertContactDTO {

    private Long id;

    private String name;

    /** 邮件收件地址；EMAIL 渠道用它 */
    private String email;

    /** 手机号；SMS 收件人 + 钉钉/企业微信机器人的 @ 对象 */
    private String mobile;

    private String remark;

    private Boolean enabled;
}
