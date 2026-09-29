package com.dataviz.alert.dto;

import lombok.Data;

import java.util.List;

/**
 * 通知组入参。
 * <p>
 * {@code contactIds} 是<b>全量覆盖</b>语义：null 表示"不改成员"（只改名字/启停时前端不必回传成员），
 * 空数组表示"清空成员"。这两个必须能区分，否则"改个组名"就会把名单冲没。
 */
@Data
public class AlertNotifyGroupDTO {

    private Long id;

    private String name;

    private String description;

    private Boolean enabled;

    private List<Long> contactIds;
}
