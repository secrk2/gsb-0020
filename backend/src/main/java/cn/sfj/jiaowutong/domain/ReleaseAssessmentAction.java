package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 解除评估流程留痕：生成 / 提交 / 审批通过 / 审批驳回 / 执行解除，
 * 每一步记录操作人、理由、动作后状态与关联上下文（如解除证明书编号）。
 */
@Entity
@Table(name = "release_assessment_action", indexes = {
        @Index(name = "idx_release_action", columnList = "assessment_id,created_at")
})
public class ReleaseAssessmentAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReleaseAssessmentActionType action;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    /** 驳回时必填理由；其余步骤可填说明 */
    @Column(length = 512)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 20)
    private ReleaseAssessmentStatus statusAfter;

    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    public ReleaseAssessmentAction() {
    }

    public ReleaseAssessmentAction(Long assessmentId, ReleaseAssessmentActionType action,
                                   Long operatorId, String operatorName, String reason,
                                   ReleaseAssessmentStatus statusAfter, String detail) {
        this.assessmentId = assessmentId;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.statusAfter = statusAfter;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getAssessmentId() { return assessmentId; }
    public ReleaseAssessmentActionType getAction() { return action; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public ReleaseAssessmentStatus getStatusAfter() { return statusAfter; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
