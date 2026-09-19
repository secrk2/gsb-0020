package cn.sfj.jiaowutong.domain;

/**
 * 违规处置单状态机（处置结论只能沿状态机推进，禁止直接改写）：
 * <pre>
 *                 ┌─→ 训诫 ADMONISHED ─→（撤销）REVOKED
 *   已登记 REGISTERED ┼─→ 收监 REIMPRISONED（终态，收监不可逆）
 *                 ├─→ 驳回 REJECTED（经查不构成违规，终态）
 *                 └─→ 撤销 REVOKED（登记有误直接撤销，终态）
 * </pre>
 * 每次推进写 disposal_action_log：操作人、理由、结论全部留痕，只追加、不改写。
 */
public enum DisposalStatus {
    REGISTERED("已登记·待处置"),
    ADMONISHED("训诫处置"),
    REIMPRISONED("收监处置"),
    REJECTED("驳回"),
    REVOKED("已撤销");

    private final String label;

    DisposalStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
