package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 解除矫正评估/审批/宣告的每一步留痕（只追加）：
 * DRAFT 生成评估 / SUBMIT 提交 / APPROVE 审批通过 / RETURN 退回补正 / RESUBMIT 重新提交 / DECLARE 宣告解除。
 * 记录谁（operator）、因为什么（reason/opinion）、推进到哪一阶段。
 */
@Entity
@Table(name = "release_action_log", indexes = {
        @Index(name = "idx_release_log", columnList = "assessment_id,id")
})
public class ReleaseActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(nullable = false, length = 24)
    private String action;

    @Column(nullable = false, length = 20)
    private String toStage;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    @Column(length = 256)
    private String reason;

    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant operatedAt;

    public ReleaseActionLog() {
    }

    public ReleaseActionLog(Long assessmentId, String action, ReleaseStage toStage,
                            Long operatorId, String operatorName, String reason, String detail) {
        this.assessmentId = assessmentId;
        this.action = action;
        this.toStage = toStage.name();
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.detail = detail;
        this.operatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public String getAction() { return action; }
    public String getToStage() { return toStage; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public String getDetail() { return detail; }
    public Instant getOperatedAt() { return operatedAt; }
}
