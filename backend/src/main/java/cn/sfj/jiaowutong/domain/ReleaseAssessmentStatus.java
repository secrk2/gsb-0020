package cn.sfj.jiaowutong.domain;

/**
 * 解除矫正评估状态机：
 * <pre>
 * 生成报告 DRAFT ──提交──→ 待审批 SUBMITTED ──审批通过──→ 已批准 APPROVED ──执行解除──→ 已解除 DONE
 *                              └──审批驳回──→ 已驳回 REJECTED ──重新提交──→ 待审批 SUBMITTED
 * </pre>
 * 只有 APPROVED 才能执行解除；执行解除同时驱动矫正档案状态机 → RELEASED 并出具永久解除标记。
 */
public enum ReleaseAssessmentStatus {
    DRAFT("待完善"),
    SUBMITTED("待审批"),
    APPROVED("已批准待执行"),
    REJECTED("审批驳回"),
    DONE("已解除归档");

    private final String label;

    ReleaseAssessmentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
