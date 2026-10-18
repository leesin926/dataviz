package com.dataviz.collab.enums;

/**
 * 审批状态。存 int 码，0/1/2 的语义与 {@code collab_approval.status} 列一致（老数据就是这个口径）。
 */
public enum ApprovalStatus {

    PENDING(0, "待审批"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已驳回");

    private final int code;
    private final String label;

    ApprovalStatus(int code, String label) {
        this.code = code;
        this.label = label;
    }

    public int getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 未知码返回 null，由调用方决定是拒绝还是兜底 —— 这里不做"默认待审批"，那会把脏数据洗成可再审一次 */
    public static ApprovalStatus of(Integer code) {
        if (code == null) {
            return null;
        }
        for (ApprovalStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        return null;
    }
}
