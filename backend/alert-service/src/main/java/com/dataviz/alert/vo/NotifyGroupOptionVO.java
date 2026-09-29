package com.dataviz.alert.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 规则表单用的通知组选项（只读，不带成员明细）。
 * <p>
 * 之所以把 {@code emailCount} / {@code mobileCount} 一起回出来：勾组的人真正要判断的不是
 * "这组有几个人"，而是<b>"这组收得到我正在选的那条渠道吗"</b>。只回名字的话，
 * "选了 EMAIL + 一个没有邮箱地址的组"这个错要等到告警真触发、通知记 FAILED 那天才看得见。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotifyGroupOptionVO {

    private Long id;

    private String name;

    private Boolean enabled;

    /** 组内联系人数（含停用） */
    private Integer memberCount;

    /** 其中<b>启用且有邮箱地址</b>的人数 */
    private Integer emailCount;

    /** 其中<b>启用且有手机号</b>的人数（短信收件人、钉钉/企微 @ 对象） */
    private Integer mobileCount;
}
