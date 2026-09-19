package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 违规处置案件动作留痕：谁、在什么时候、做了哪一步、因为什么。
 * 处置结论只能通过追加动作推进，不允许直接修改案件状态字段。
 */
@Entity
@Table(name = "violation_case_action", indexes = {
        @Index(name = "idx_vcase_action_case", columnList = "case_id,created_at")
})
public class ViolationCaseAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationActionType action;

    @Column(nullable = false)
    private Long operatorId;

    @Column(nullable = false, length = 64)
    private String operatorName;

    /** 必填理由（驳回/撤销/训诫/收监都必须说明依据） */
    @Column(nullable = false, length = 256)
    private String reason;

    /** 动作后案件状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_after", nullable = false, length = 20)
    private ViolationCaseStatus statusAfter;

    /** 关联上下文，如触发的矫正状态流转 id、合并的预警条数 */
    @Column(length = 256)
    private String detail;

    @Column(nullable = false)
    private Instant createdAt;

    public ViolationCaseAction() {
    }

    public ViolationCaseAction(Long caseId, ViolationActionType action, Long operatorId, String operatorName,
                               String reason, ViolationCaseStatus statusAfter, String detail) {
        this.caseId = caseId;
        this.action = action;
        this.operatorId = operatorId;
        this.operatorName = operatorName;
        this.reason = reason;
        this.statusAfter = statusAfter;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getCaseId() { return caseId; }
    public ViolationActionType getAction() { return action; }
    public Long getOperatorId() { return operatorId; }
    public String getOperatorName() { return operatorName; }
    public String getReason() { return reason; }
    public ViolationCaseStatus getStatusAfter() { return statusAfter; }
    public String getDetail() { return detail; }
    public Instant getCreatedAt() { return createdAt; }
}
