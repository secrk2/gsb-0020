package cn.sfj.jiaowutong.domain;

/**
 * 违规处置案件状态。
 * 流转规则见 {@link cn.sfj.jiaowutong.service.ViolationCaseStateMachine}：
 * REGISTERED 待处置 → ADMONISHED 已训诫 / REIMPRISONED 已收监 / REJECTED 已驳回 / REVOKED 已撤销。
 */
public enum ViolationCaseStatus {
    REGISTERED("待处置"),
    ADMONISHED("已训诫"),
    REIMPRISONED("已收监"),
    REJECTED("已驳回"),
    REVOKED("已撤销");

    private final String label;

    ViolationCaseStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
