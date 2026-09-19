package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 解除矫正评估报告与解除状态机载体。
 * 到期对象先生成评估报告（DRAFT），提交→审批→宣告解除，宣告时联动档案置「解除」终态
 * 并打永久标记 releasedPermanently，签发解除证明书编号。
 * 每一步（生成/提交/审批通过/退回/重新提交/宣告）写 {@link ReleaseActionLog}，只追加。
 */
@Entity
@Table(name = "release_assessment", indexes = {
        @Index(name = "idx_release_office_stage", columnList = "office_id,stage,created_at"),
        @Index(name = "idx_release_offender", columnList = "offender_id")
})
public class ReleaseAssessment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 评估报告业务编号，唯一，如 PG-20260919-0001 */
    @Column(nullable = false, unique = true, length = 32)
    private String assessmentNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReleaseStage stage = ReleaseStage.DRAFT;

    /** 矫正期满日（取档案 endDate 快照） */
    private LocalDate endDate;

    // ---------------- 评估报告内容 ----------------
    /** 综合考核评分 0-100 */
    private Integer score;
    /** 教育学习情况 */
    @Column(length = 512)
    private String education;
    /** 日常监管/报到/定位合规情况 */
    @Column(length = 512)
    private String compliance;
    /** 认罪悔罪与思想动态 */
    @Column(length = 512)
    private String repentance;
    /** 再犯罪风险评估等级：LOW/MEDIUM/HIGH */
    @Column(length = 8)
    private String riskLevel;
    /** 评估结论与建议 */
    @Column(length = 512)
    private String conclusion;
    /** 评估人（报告制作人） */
    @Column(length = 64)
    private String assessorName;
    private Instant assessedAt;

    // ---------------- 提交 ----------------
    private Long submittedBy;
    @Column(length = 64)
    private String submittedByName;
    private Instant submittedAt;

    // ---------------- 审批 ----------------
    private Long approvedBy;
    @Column(length = 64)
    private String approvedByName;
    private Instant approvedAt;
    /** 审批意见/退回说明 */
    @Column(length = 512)
    private String approvalOpinion;

    // ---------------- 宣告解除 ----------------
    private Long declaredBy;
    @Column(length = 64)
    private String declaredByName;
    private Instant declaredAt;
    /** 解除证明书编号（=档案 releaseCertificateNo） */
    @Column(length = 40)
    private String certificateNo;

    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public ReleaseAssessment() {
    }

    public Long getId() { return id; }
    public String getAssessmentNo() { return assessmentNo; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public ReleaseStage getStage() { return stage; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getScore() { return score; }
    public String getEducation() { return education; }
    public String getCompliance() { return compliance; }
    public String getRepentance() { return repentance; }
    public String getRiskLevel() { return riskLevel; }
    public String getConclusion() { return conclusion; }
    public String getAssessorName() { return assessorName; }
    public Instant getAssessedAt() { return assessedAt; }
    public Long getSubmittedBy() { return submittedBy; }
    public String getSubmittedByName() { return submittedByName; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Long getApprovedBy() { return approvedBy; }
    public String getApprovedByName() { return approvedByName; }
    public Instant getApprovedAt() { return approvedAt; }
    public String getApprovalOpinion() { return approvalOpinion; }
    public Long getDeclaredBy() { return declaredBy; }
    public String getDeclaredByName() { return declaredByName; }
    public Instant getDeclaredAt() { return declaredAt; }
    public String getCertificateNo() { return certificateNo; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setAssessmentNo(String assessmentNo) { this.assessmentNo = assessmentNo; }
    public void setOffender(CorrectionObject offender) {
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
    }
    public void setStage(ReleaseStage stage) { this.stage = stage; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public void setScore(Integer score) { this.score = score; }
    public void setEducation(String education) { this.education = education; }
    public void setCompliance(String compliance) { this.compliance = compliance; }
    public void setRepentance(String repentance) { this.repentance = repentance; }
    public void setRiskLevel(String riskLevel) { this.riskLevel = riskLevel; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public void setAssessorName(String assessorName) { this.assessorName = assessorName; }
    public void setAssessedAt(Instant assessedAt) { this.assessedAt = assessedAt; }
    public void setSubmittedBy(Long submittedBy) { this.submittedBy = submittedBy; }
    public void setSubmittedByName(String submittedByName) { this.submittedByName = submittedByName; }
    public void setSubmittedAt(Instant submittedAt) { this.submittedAt = submittedAt; }
    public void setApprovedBy(Long approvedBy) { this.approvedBy = approvedBy; }
    public void setApprovedByName(String approvedByName) { this.approvedByName = approvedByName; }
    public void setApprovedAt(Instant approvedAt) { this.approvedAt = approvedAt; }
    public void setApprovalOpinion(String approvalOpinion) { this.approvalOpinion = approvalOpinion; }
    public void setDeclaredBy(Long declaredBy) { this.declaredBy = declaredBy; }
    public void setDeclaredByName(String declaredByName) { this.declaredByName = declaredByName; }
    public void setDeclaredAt(Instant declaredAt) { this.declaredAt = declaredAt; }
    public void setCertificateNo(String certificateNo) { this.certificateNo = certificateNo; }
}
