package cn.sfj.jiaowutong.domain;

/**
 * 解除评估流程动作（每一步落 release_assessment_action 留痕）。
 */
public enum ReleaseAssessmentActionType {
    GENERATE("生成评估报告"),
    SUBMIT("提交评估"),
    APPROVE("审批通过"),
    REJECT("审批驳回"),
    EXECUTE("执行解除");

    private final String label;

    ReleaseAssessmentActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
