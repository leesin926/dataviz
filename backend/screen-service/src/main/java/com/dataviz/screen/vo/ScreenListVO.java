package com.dataviz.screen.vo;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

/**
 * 大屏分页结果，字段名与前端 shared-types PageResult（list/pages）对齐。
 */
@Data
@AllArgsConstructor
public class ScreenListVO {

    private List<ScreenVO> list;

    private long total;

    private int pageNum;

    private int pageSize;

    private int pages;
}
