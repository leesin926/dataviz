package com.dataviz.common.core.result;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应结果。
 *
 * <p>{@code truncated} / {@code shards} / {@code windowDefaulted} 三个字段是<b>可选</b>的（默认 {@code null}）：
 * 只有分片读路径会填它们，其余模块的响应体一个字节都不变。为什么要把它们放进这个公共 DTO 而不是各自加壳：
 * "这一页不完整"必须让前端能画出来，而前端画的东西只能来自响应体 —— 走 HTTP 头或者只写日志，
 * 用户看到的仍然是一页少了几行、并且以为那就是全部。
 */
@Data
@NoArgsConstructor
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> records;
    private long total;
    private int pageNum;
    private int pageSize;
    private int totalPages;

    /** 结果不完整（扇出被分片上限截断）。{@code null} = 本接口不分片，与今天一致。 */
    private Boolean truncated;

    /** 本次实际扫描的物理分片数；只在分片读路径出现。 */
    private Integer shards;

    /** 调用方没给时间范围、服务端按默认窗口收窄了查询。为真时页面必须把实际范围说出来。 */
    private Boolean windowDefaulted;

    public PageResult(List<T> records, long total, int pageNum, int pageSize, int totalPages) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.totalPages = totalPages;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int pageNum, int pageSize) {
        int totalPages = (int) Math.ceil((double) total / pageSize);
        return new PageResult<>(records, total, pageNum, pageSize, totalPages);
    }

    public static <T> PageResult<T> empty(int pageNum, int pageSize) {
        return new PageResult<>(Collections.emptyList(), 0L, pageNum, pageSize, 0);
    }

    /**
     * 分片读路径的分页结果。三个可空字段一次填齐，不留"有 shards 却没说 truncated"这种半张脸。
     *
     * @param truncated      扇出被 {@code shard.max-shards-per-read} 截断 ⇒ 这一页与 total 都不完整
     * @param shards         实际扫描的物理分片数
     * @param windowDefaulted 调用方没给时间范围、由服务端按默认窗口收窄
     */
    public static <T> PageResult<T> ofSharded(List<T> records, long total, int pageNum, int pageSize,
                                              boolean truncated, int shards, boolean windowDefaulted) {
        PageResult<T> result = of(records, total, pageNum, pageSize);
        result.setTruncated(truncated);
        result.setShards(shards);
        result.setWindowDefaulted(windowDefaulted);
        return result;
    }

    public boolean hasRecords() {
        return records != null && !records.isEmpty();
    }

    public boolean hasNext() {
        return pageNum < totalPages;
    }
}
