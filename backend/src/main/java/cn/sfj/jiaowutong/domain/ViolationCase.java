package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 违规处置案件：把越界、禁区闯入、未报到等原始预警事件（{@link ViolationEvent}）
 * 登记为可处置的案件。结论不允许直接涂改：
 * 登记 → 训诫 / 收监 / 驳回 / 撤销，每一步均由 {@link ViolationCaseAction} 留痕，
 * 训诫与收监同时走矫正档案状态机（{@link StatusTransition}）。
 */
@Entity
@Table(name = "violation_case",
        uniqueConstraints = @UniqueConstraint(name = "uk_violation_case_no", columnNames = "case_no"),
        indexes = {
                @Index(name = "idx_vcase_office_status", columnList = "office_id,status"),
                @Index(name = "idx_vcase_offender_time", columnList = "offender_id,registered_at")
        })
public class ViolationCase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 案件编号，如 AJ26-0001 */
    @Column(name = "case_no", nullable = false, length = 32)
    private String caseNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    /** 事由类型：GEOFENCE_BREACH 越界 / FORBIDDEN_ZONE 禁区闯入 / ABSENT 未报到 / OTHER 其他 */
    @Column(name = "reason_type", nullable = false, length = 32)
    private String reasonType;

    /** 登记事由摘要 */
    @Column(nullable = false, length = 256)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ViolationCaseStatus status = ViolationCaseStatus.REGISTERED;

    @Column(nullable = false)
    private Instant registeredAt;

    /** 终态时间（训诫/收监/驳回/撤销） */
    private Instant closedAt;

    @Column(nullable = false)
    private Long registeredBy;

    @Column(nullable = false, length = 64)
    private String registeredByName;

    public ViolationCase() {
    }

    public ViolationCase(String caseNo, CorrectionObject offender, String reasonType, String summary,
                         Long registeredBy, String registeredByName) {
        this.caseNo = caseNo;
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
        this.reasonType = reasonType;
        this.summary = summary;
        this.registeredBy = registeredBy;
        this.registeredByName = registeredByName;
        this.registeredAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getCaseNo() { return caseNo; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public String getReasonType() { return reasonType; }
    public String getSummary() { return summary; }
    public ViolationCaseStatus getStatus() { return status; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getClosedAt() { return closedAt; }
    public Long getRegisteredBy() { return registeredBy; }
    public String getRegisteredByName() { return registeredByName; }

    public void setStatus(ViolationCaseStatus status) { this.status = status; }
    public void setClosedAt(Instant closedAt) { this.closedAt = closedAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
}
