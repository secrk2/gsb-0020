package cn.sfj.jiaowutong.domain;

/**
 * 解除矫正状态机（走完才打永久解除标记）：
 * <pre>
 *   到期清单 ─生成评估→ DRAFT 评估草稿
 *     DRAFT ─提交→ SUBMITTED 待审批
 *     SUBMITTED ─审批通过→ APPROVED 待宣告
 *     SUBMITTED ─退回→ REJECTED（补正评估后可重新提交）
 *     REJECTED ─重新提交→ SUBMITTED
 *     APPROVED ─宣告解除→ DECLARED（终态，联动档案→解除，打永久标记、冻结定位）
 * </pre>
 * 只有 DECLARED 会置 releasedPermanently=true；该标记不可逆。
 */
public enum ReleaseStage {
    DRAFT("评估报告·草稿"),
    SUBMITTED("已提交·待审批"),
    APPROVED("审批通过·待宣告"),
    REJECTED("审批退回·待补正"),
    DECLARED("已宣告解除");

    private final String label;

    ReleaseStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
