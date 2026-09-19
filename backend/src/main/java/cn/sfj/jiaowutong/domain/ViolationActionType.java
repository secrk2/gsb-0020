package cn.sfj.jiaowutong.domain;

/**
 * 违规处置案件上的动作（每一步都落 violation_case_action 留痕）。
 */
public enum ViolationActionType {
    REGISTER("登记受理"),
    ADMONISH("予以训诫"),
    REIMPRISON("提请收监"),
    REJECT("驳回"),
    REVOKE("撤销案件");

    private final String label;

    ViolationActionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
