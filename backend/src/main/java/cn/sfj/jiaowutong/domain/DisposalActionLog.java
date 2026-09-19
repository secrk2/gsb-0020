package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 违规处置单的每一步操作留痕（只追加，不提供更新/删除）：
 * REGISTER 登记 / ADMONISH 训诫 / REIMPRISON 收监 / REJECT 驳回 / REVOKE 撤销。
 * 谁做的（operatorId/Name）、因为什么（reason）、做了什么（action/conclusion）全部可回溯。
 */
@Entity
@Table(name = "disposal_action_log", indexes = {
        @Index(name = "idx_disposal_log_record", columnList = "disposal_id,id")
})
public class DisposalActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "disposal_id", nullable = false)
    private Long disposalId;

    /** 处置阶段动作 */
    @Column(nullable = false, length = 24)
    private String action;

    /** 该步要进入的处置状态（DisposalStatus） */
    @Column(nullable = false, length = 20)
    private String toStatus;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Column(nullable = false, length = 256)
    private String reason;

    /** 附加上下文（如联动的档案状态流转、核销红点数量） */
    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant operatedAt;

    public DisposalActionLog() {
    }

    public DisposalActionLog(Long disposalId, String action, DisposalStatus toStatus,
                             Long operatorId, String operatorName, String reason, String detail) {
        this.disposalId = disposalId;
        this.action = action;
        this.toStatus = toStatus.name();
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.detail = detail;
        this.operatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getDisposalId() { return disposalId; }
    public String getAction() { return action; }
    public String getToStatus() { return toStatus; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public Instant getOperatedAt() { return operatedAt; }
}
