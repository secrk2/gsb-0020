package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 解除矫正评估报告。矫正期满对象先生成报告（含报到完成度、违规处置、定位活跃度等客观数据），
 * 再走 待完善 → 待审批 → 已批准 → 已解除归档 状态机；驳回可修改后重新提交。
 * 审批通过并执行解除后出具永久解除标记（解除证明书编号），执行解除同时驱动矫正档案状态机。
 */
@Entity
@Table(name = "release_assessment",
        uniqueConstraints = @UniqueConstraint(name = "uk_release_report_no", columnNames = "report_no"),
        indexes = {
                @Index(name = "idx_release_office_status", columnList = "office_id,status"),
                @Index(name = "idx_release_offender", columnList = "offender_id")
        })
public class ReleaseAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 评估报告编号，如 PG26-0001 */
    @Column(name = "report_no", nullable = false, length = 32)
    private String reportNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReleaseAssessmentStatus status = ReleaseAssessmentStatus.DRAFT;

    /** 评估依据：矫正期满日（对象档案 endDate 快照） */
    @Column(nullable = false)
    private LocalDate dueDate;

    // -------- 报告客观数据（生成时固化，之后不再随业务数据变化） --------
    /** 近 30 天打卡天数口径 */
    private Double checkinDayRate;
    /** 近 30 天关键报到口径 */
    private Double keyReportRate;
    /** 近 30 天有定位天数 */
    private Integer trackActiveDays;
    /** 近 30 天越界/禁区预警条数 */
    private Integer breachCount30d;
    /** 矫正期内训诫次数（以状态流转计） */
    private Integer admonishCount;
    /** 生成时是否存在待处置违规案件 */
    private Boolean openViolationCase;

    /** 评估结论：SUGGEST_RELEASE 建议按期解除 / CONTINUE_EDUCATION 建议延长教育 */
    @Column(name = "conclusion", length = 32)
    private String conclusion;

    /** 干警综合鉴定意见 */
    @Column(length = 512)
    private String opinion;

    @Column(nullable = false)
    private Long generatedBy;
    @Column(nullable = false, length = 64)
    private String generatedByName;
    @Column(nullable = false)
    private Instant generatedAt;

    private Instant submittedAt;
    private Long approvedBy;
    @Column(length = 64)
    private String approvedByName;
    private Instant approvedAt;
    private Instant rejectedAt;

    /** 执行解除时间（永久标记出具时刻） */
    private Instant releasedAt;
    /** 解除证明书编号（永久标记，出具后不可更改） */
    @Column(name = "release_certificate_no", length = 32)
    private String releaseCertificateNo;
    private Long releasedBy;
    @Column(length = 64)
    private String releasedByName;

    public ReleaseAssessment() {
    }

    public ReleaseAssessment(String reportNo, CorrectionObject offender, LocalDate dueDate,
                             Long generatedBy, String generatedByName) {
        this.reportNo = reportNo;
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
        this.dueDate = dueDate;
        this.generatedBy = generatedBy;
        this.generatedByName = generatedByName;
        this.generatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getReportNo() { return reportNo; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public ReleaseAssessmentStatus getStatus() { return status; }
    public LocalDate getDueDate() { return dueDate; }
    public Double getCheckinDayRate() { return checkinDayRate; }
    public Double getKeyReportRate() { return keyReportRate; }
    public Integer getTrackActiveDays() { return trackActiveDays; }
    public Integer getBreachCount30d() { return breachCount30d; }
    public Integer getAdmonishCount() { return admonishCount; }
    public Boolean getOpenViolationCase() { return openViolationCase; }
    public String getConclusion() { return conclusion; }
    public String getOpinion() { return opinion; }
    public Long getGeneratedBy() { return generatedBy; }
    public String getGeneratedByName() { return generatedByName; }
    public Instant getGeneratedAt() { return generatedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Long getApprovedBy() { return approvedBy; }
    public String getApprovedByName() { return approvedByName; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getReleasedAt() { return releasedAt; }
    public String getReleaseCertificateNo() { return releaseCertificateNo; }
    public Long getReleasedBy() { return releasedBy; }
    public String getReleasedByName() { return releasedByName; }

    public void setStatus(ReleaseAssessmentStatus status) { this.status = status; }
    public void setCheckinDayRate(Double checkinDayRate) { this.checkinDayRate = checkinDayRate; }
    public void setKeyReportRate(Double keyReportRate) { this.keyReportRate = keyReportRate; }
    public void setTrackActiveDays(Integer trackActiveDays) { this.trackActiveDays = trackActiveDays; }
    public void setBreachCount30d(Integer breachCount30d) { this.breachCount30d = breachCount30d; }
    public void setAdmonishCount(Integer admonishCount) { this.admonishCount = admonishCount; }
    public void setOpenViolationCase(Boolean openViolationCase) { this.openViolationCase = openViolationCase; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public void setOpinion(String opinion) { this.opinion = opinion; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setRejectedAt(Instant rejectedAt) { this.rejectedAt = rejectedAt; }
    public void setReleasedAt(Instant releasedAt) { this.releasedAt = releasedAt; }
    public void setReleaseCertificateNo(String releaseCertificateNo) { this.releaseCertificateNo = releaseCertificateNo; }
    public void setReleasedBy(Long releasedBy) { this.releasedBy = releasedBy; }
    public void setReleasedByName(String releasedByName) { this.releasedByName = releasedByName; }
}
