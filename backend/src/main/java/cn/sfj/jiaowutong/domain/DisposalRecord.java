package cn.sfj.jiaowutong.domain;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * 违规处置单：把越界、未报到等红点事件“登记”为可处置的记录。
 * <p>去重口径：同一对象（offender）+ 同一事由（category）+ 时间窗（DISPOSAL_MERGE_WINDOW_HOURS）
 * 内只合成/关联一条处于“已登记·待处置”的记录，事件不断追加，不重复开单，避免一晚刷出七八条。</p>
 * <p>结论不可直接改写：status 只能经 {@link cn.sfj.jiaowutong.service.DisposalStateMachine}
 * 推进，每一步写 {@link DisposalActionLog}。</p>
 */
@Entity
@Table(name = "disposal_record", indexes = {
        @Index(name = "idx_disposal_office_status", columnList = "office_id,status,registered_at"),
        @Index(name = "idx_disposal_offender", columnList = "offender_id,registered_at")
})
public class DisposalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 处置单业务编号，唯一，如 CC-20260919-0001 */
    @Column(nullable = false, unique = true, length = 32)
    private String disposalNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offender_id")
    private CorrectionObject offender;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private DisposalCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DisposalStatus status = DisposalStatus.REGISTERED;

    /** 事由标题（一句话概括） */
    @Column(nullable = false, length = 128)
    private String title;

    /** 登记说明/初步核查情况 */
    @Column(length = 512)
    private String detail;

    /** 来源：AUTO 由红点事件合成 / MANUAL 干警手工登记 */
    @Column(nullable = false, length = 8)
    private String source = "MANUAL";

    /** 合并进本单的事件数（时间窗内同一对象同一事由） */
    @Column(nullable = false)
    private int eventCount = 0;

    /** 被合并事件的最早/最晚发生时间（UTC） */
    private Instant firstEventAt;
    private Instant lastEventAt;

    /** 登记人 */
    @Column(nullable = false)
    private Long registeredBy;
    @Column(nullable = false, length = 64)
    private String registeredByName;
    @Column(length = 256)
    private String registerReason;
    @Column(nullable = false)
    private Instant registeredAt;

    /** 办结（训诫/收监/驳回/撤销）时间，待处置时为空 */
    private Instant resolvedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (registeredAt == null) {
            registeredAt = createdAt;
        }
    }

    public DisposalRecord() {
    }

    public Long getId() { return id; }
    public String getDisposalNo() { return disposalNo; }
    public CorrectionObject getOffender() { return offender; }
    public Long getOfficeId() { return officeId; }
    public DisposalCategory getCategory() { return category; }
    public DisposalStatus getStatus() { return status; }
    public String getTitle() { return title; }
    public String getDetail() { return detail; }
    public String getSource() { return source; }
    public int getEventCount() { return eventCount; }
    public Instant getFirstEventAt() { return firstEventAt; }
    public Instant getLastEventAt() { return lastEventAt; }
    public Long getRegisteredBy() { return registeredBy; }
    public String getRegisteredByName() { return registeredByName; }
    public String getRegisterReason() { return registerReason; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisposalNo(String disposalNo) { this.disposalNo = disposalNo; }
    public void setOffender(CorrectionObject offender) {
        this.offender = offender;
        this.officeId = offender.getOffice().getId();
    }
    public void setCategory(DisposalCategory category) { this.category = category; }
    public void setStatus(DisposalStatus status) { this.status = status; }
    public void setTitle(String title) { this.title = title; }
    public void setDetail(String detail) { this.detail = detail; }
    public void setSource(String source) { this.source = source; }
    public void setEventCount(int eventCount) { this.eventCount = eventCount; }
    public void setFirstEventAt(Instant firstEventAt) { this.firstEventAt = firstEventAt; }
    public void setLastEventAt(Instant lastEventAt) { this.lastEventAt = lastEventAt; }
    public void setRegisteredBy(Long registeredBy) { this.registeredBy = registeredBy; }
    public void setRegisteredByName(String registeredByName) { this.registeredByName = registeredByName; }
    public void setRegisterReason(String registerReason) { this.registerReason = registerReason; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
