package com.dataviz.common.core.result;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import lombok.Data;

/**
 * 分页请求参数
 */
@Data
public class PageQuery {

    @Min(value = 1, message = "页码最小值为1")
    private int pageNum = 1;

    @Min(value = 1, message = "每页条数最小值为1")
    @Max(value = 500, message = "每页条数最大值为500")
    private int pageSize = 10;

    private String orderBy;

    private boolean asc = true;

    /**
     * 计算偏移量
     */
    public int getOffset() {
        return (pageNum - 1) * pageSize;
    }
}
