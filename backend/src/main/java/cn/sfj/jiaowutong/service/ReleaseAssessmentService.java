package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.ReleaseAssessmentRequest;
import cn.sfj.jiaowutong.web.dto.ReleaseDecisionRequest;
import cn.sfj.jiaowutong.web.vo.CompletionView;
import cn.sfj.jiaowutong.web.vo.ReleaseAssessmentView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 解除与评估：
 * 1. 到期名单：按对象所在司法所时区判定矫正期满日（endDate），临期 {@link #DUE_SOON_DAYS}
 *    天及已到期的监外执行对象进入名单；
 * 2. 评估报告先行：先生成报告（报到双口径、定位活跃、30 天越界、训诫次数、待处置案件），
 *    客观数据生成时固化，再走 待完善→待审批→已批准→已解除归档 状态机，驳回可重新提交；
 * 3. 执行解除：只能在评估已批准后执行，驱动矫正档案状态机 → RELEASED，出具永久解除证明书
 *    编号，核销该对象全部未处置红点，清空并冻结实时位置（之后不再接收定位更新）；
 * 4. 解除后：档案仍可按矫正编号检索，但不出现在在矫名单、定位监控与作战台红点中。
 */
@Service
public class ReleaseAssessmentService {

    /** 期满前多少天进入“临期”名单 */
    public static final long DUE_SOON_DAYS = 30;

    private static final DateTimeFormatter NO_YEAR_FMT = DateTimeFormatter.ofPattern("yy");
    private static final Set<CorrectionStatus> ACTIVE = Set.of(
            CorrectionStatus.SERVING, CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED);

    private final ReleaseAssessmentRepository assessmentRepository;
    private final ReleaseAssessmentActionRepository actionRepository;
    private final CorrectionObjectRepository objectRepository;
    private final ViolationEventRepository eventRepository;
    private final ViolationCaseRepository caseRepository;
    private final StatusTransitionRepository transitionRepository;
    private final TrackPointRepository trackPointRepository;
    private final AccessControlService accessControl;
    private final CorrectionTransitionService transitionService;
    private final MonitorService monitorService;

    public ReleaseAssessmentService(ReleaseAssessmentRepository assessmentRepository,
                                    ReleaseAssessmentActionRepository actionRepository,
                                    CorrectionObjectRepository objectRepository,
                                    ViolationEventRepository eventRepository,
                                    ViolationCaseRepository caseRepository,
                                    StatusTransitionRepository transitionRepository,
                                    TrackPointRepository trackPointRepository,
                                    AccessControlService accessControl,
                                    CorrectionTransitionService transitionService,
                                    MonitorService monitorService) {
        this.assessmentRepository = assessmentRepository;
        this.actionRepository = actionRepository;
        this.objectRepository = objectRepository;
        this.eventRepository = eventRepository;
        this.caseRepository = caseRepository;
        this.transitionRepository = transitionRepository;
        this.trackPointRepository = trackPointRepository;
        this.accessControl = accessControl;
        this.transitionService = transitionService;
        this.monitorService = monitorService;
    }

    // ---------------- 到期名单 ----------------

    @Transactional(readOnly = true)
    public List<ReleaseAssessmentView.DueItem> dueList(LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        List<ReleaseAssessmentView.DueItem> items = new ArrayList<>();
        for (CorrectionObject o : accessControl.filterByScope(objectRepository.findAll(), user)) {
            if (o.getEndDate() == null) {
                continue;
            }
            // “今天/是否到期”按对象所在司法所时区判定，跨时区对象不记错日子
            LocalDate today = Instant.now()
                    .atZone(FenceService.safeZone(o.getOffice().getTimezone())).toLocalDate();
            long daysToDue = java.time.temporal.ChronoUnit.DAYS.between(today, o.getEndDate());
            if (daysToDue > DUE_SOON_DAYS) {
                continue;
            }
            // 已解除对象仅在确实已归档时不进入名单；未解除的（含已过期未办手续）继续提醒
            if (o.getStatus() == CorrectionStatus.RELEASED
                    || o.getStatus() == CorrectionStatus.REIMPRISONED) {
                continue;
            }
            Optional<ReleaseAssessment> open = assessmentRepository
                    .findFirstByOffender_IdAndStatusNotOrderByIdDesc(
                            o.getId(), ReleaseAssessmentStatus.DONE);
            items.add(new ReleaseAssessmentView.DueItem(
                    o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                    o.getOffice().getName(), o.getOffice().getTimezone(),
                    o.getStatus().name(), o.getStatus().getLabel(),
                    o.getEndDate(), daysToDue, daysToDue < 0,
                    open.map(ReleaseAssessment::getId).orElse(null),
                    open.map(ReleaseAssessment::getReportNo).orElse(null),
                    open.map(a -> a.getStatus().name()).orElse(null),
                    open.map(a -> a.getStatus().getLabel()).orElse(null)));
        }
        items.sort(java.util.Comparator.comparing(ReleaseAssessmentView.DueItem::endDate));
        return items;
    }

    // ---------------- 报告列表 / 详情 ----------------

    @Transactional(readOnly = true)
    public List<ReleaseAssessmentView> list(ReleaseAssessmentStatus status, Long officeId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        return assessmentRepository.findAllByOrderByGeneratedAtDescIdDesc().stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> officeId == null || officeId.equals(a.getOfficeId()))
                .filter(a -> canView(a, user))
                .map(this::toView)
                .toList();
    }

    private boolean canView(ReleaseAssessment a, LoginUser user) {
        try {
            accessControl.assertCanView(a.getOffender(), user);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /** 对象档案详情：该对象最近一份解除评估（含已解除归档），无则 null */
    @Transactional(readOnly = true)
    public ReleaseAssessmentView latestViewForOffender(Long offenderId, LoginUser user) {
        return assessmentRepository.findByOffender_IdOrderByGeneratedAtDescIdDesc(offenderId).stream()
                .findFirst()
                .filter(a -> canView(a, user))
                .map(this::toView)
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ReleaseAssessmentView.DetailView detail(Long id, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        List<ReleaseAssessmentView.ActionView> actions =
                actionRepository.findByAssessmentIdOrderByCreatedAtAscIdAsc(id).stream()
                        .map(x -> new ReleaseAssessmentView.ActionView(
                                x.getId(), x.getAction().name(), x.getAction().getLabel(),
                                x.getOperatorName(), x.getReason(),
                                x.getStatusAfter().name(), x.getStatusAfter().getLabel(),
                                x.getDetail(), x.getCreatedAt()))
                        .toList();
        CorrectionObject o = a.getOffender();
        return new ReleaseAssessmentView.DetailView(toView(a), actions,
                o.getStatus().name(), o.getStatus().getLabel(),
                o.getCharge(), o.getStartDate(), o.getEndDate());
    }

    // ---------------- 生成评估报告（到期对象） ----------------

    @Transactional
    public ReleaseAssessmentView generate(Long objectId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(objectId, user);
        if (o.getStatus() == CorrectionStatus.RELEASED) {
            throw new ApiException("INVALID_ACTION", "对象已解除矫正并永久归档，不能再生成评估报告");
        }
        if (o.getStatus() == CorrectionStatus.REIMPRISONED) {
            throw new ApiException("INVALID_ACTION", "对象已收监执行，社区矫正流程终结，不适用解除评估");
        }
        Optional<ReleaseAssessment> existed = assessmentRepository
                .findFirstByOffender_IdAndStatusNotOrderByIdDesc(objectId, ReleaseAssessmentStatus.DONE);
        if (existed.isPresent()) {
            throw new ApiException("ASSESSMENT_EXISTS",
                    "该对象已有在途评估报告「" + existed.get().getReportNo()
                            + "」（" + existed.get().getStatus().getLabel()
                            + "），请在原报告上继续流程，不要重复生成");
        }
        if (o.getEndDate() == null) {
            throw new ApiException("INVALID_ACTION", "档案缺少矫正期满日，无法生成解除评估报告");
        }

        ReleaseAssessment a = new ReleaseAssessment(nextReportNo(), o, o.getEndDate(),
                user.userId(), user.realName());

        // 客观数据固化：与定位监控同一套完成度口径
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        CompletionView completion = monitorService.buildCompletion(o, zone);
        a.setCheckinDayRate(completion.checkinDayRate());
        a.setKeyReportRate(completion.keyReportRate());

        Instant now = Instant.now();
        Instant floor30 = now.minusSeconds(30L * 86400);
        long trackDays = trackPointRepository
                .findByOffender_IdAndResultAndPointTimeAfterOrderByPointTimeAscIdAsc(
                        objectId, TrackPoint.IngestResult.ACCEPTED, floor30).stream()
                .map(p -> p.getPointTime().atZone(zone).toLocalDate())
                .distinct().count();
        a.setTrackActiveDays((int) trackDays);
        a.setBreachCount30d((int) eventRepository.countByOffender_IdAndTypeInAndEventTimeAfter(
                objectId, List.of("GEOFENCE_BREACH", "FORBIDDEN_ZONE"), floor30));
        a.setAdmonishCount((int) transitionRepository.countByOffenderIdAndToStatus(
                objectId, CorrectionStatus.ADMONISHED.name()));
        boolean openCase = caseRepository.existsByOffender_IdAndStatus(
                objectId, ViolationCaseStatus.REGISTERED);
        a.setOpenViolationCase(openCase);

        // 机器建议（仅供干警参考，结论由干警填写）
        boolean risky = openCase || completion.keyReportRate() < 0.6d;
        a.setConclusion(risky ? "CONTINUE_EDUCATION" : "SUGGEST_RELEASE");

        assessmentRepository.save(a);
        actionRepository.save(new ReleaseAssessmentAction(a.getId(), ReleaseAssessmentActionType.GENERATE,
                user.userId(), user.realName(),
                "生成解除矫正评估报告（客观数据生成时固化）", ReleaseAssessmentStatus.DRAFT,
                "近30天打卡天数 " + pct(a.getCheckinDayRate())
                        + "、关键报到 " + pct(a.getKeyReportRate())
                        + "、定位活跃 " + a.getTrackActiveDays() + " 天"
                        + "、越界/禁区 " + a.getBreachCount30d() + " 次"
                        + "、累计训诫 " + a.getAdmonishCount() + " 次"
                        + (openCase ? "、存在待处置违规案件" : "")));
        return toView(a);
    }

    /** 修改报告综合鉴定意见（仅待完善/驳回态） */
    @Transactional
    public ReleaseAssessmentView update(Long id, ReleaseAssessmentRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        if (a.getStatus() != ReleaseAssessmentStatus.DRAFT
                && a.getStatus() != ReleaseAssessmentStatus.REJECTED) {
            throw new ApiException("INVALID_ACTION",
                    "报告已提交审批（" + a.getStatus().getLabel() + "），不能再修改鉴定意见");
        }
        if (req.opinion() != null && !req.opinion().isBlank()) {
            a.setOpinion(req.opinion());
        }
        if (req.conclusion() != null && !req.conclusion().isBlank()) {
            assertConclusion(req.conclusion());
            a.setConclusion(req.conclusion());
        }
        assessmentRepository.save(a);
        return toView(a);
    }

    // ---------------- 提交 / 审批 / 执行解除 ----------------

    @Transactional
    public ReleaseAssessmentView submit(Long id, ReleaseDecisionRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        ReleaseAssessmentStateMachine.assertTransition(a.getStatus(), ReleaseAssessmentStatus.SUBMITTED);
        if (a.getConclusion() == null || a.getConclusion().isBlank()) {
            throw new ApiException("INVALID_ACTION", "请先填写评估结论后再提交审批");
        }
        a.setStatus(ReleaseAssessmentStatus.SUBMITTED);
        a.setSubmittedAt(Instant.now());
        assessmentRepository.save(a);
        actionRepository.save(new ReleaseAssessmentAction(a.getId(), ReleaseAssessmentActionType.SUBMIT,
                user.userId(), user.realName(), req.reason(), ReleaseAssessmentStatus.SUBMITTED,
                "结论：" + conclusionLabel(a.getConclusion())));
        return toView(a);
    }

    @Transactional
    public ReleaseAssessmentView approve(Long id, ReleaseDecisionRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        ReleaseAssessmentStateMachine.assertTransition(a.getStatus(), ReleaseAssessmentStatus.APPROVED);
        a.setStatus(ReleaseAssessmentStatus.APPROVED);
        a.setApprovedAt(Instant.now());
        a.setApprovedBy(user.userId());
        a.setApprovedByName(user.realName());
        assessmentRepository.save(a);
        actionRepository.save(new ReleaseAssessmentAction(a.getId(), ReleaseAssessmentActionType.APPROVE,
                user.userId(), user.realName(), req.reason(), ReleaseAssessmentStatus.APPROVED,
                "评估审批通过，可执行解除"));
        return toView(a);
    }

    @Transactional
    public ReleaseAssessmentView reject(Long id, ReleaseDecisionRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        ReleaseAssessmentStateMachine.assertTransition(a.getStatus(), ReleaseAssessmentStatus.REJECTED);
        if (req.reason() == null || req.reason().isBlank()) {
            throw new ApiException("VALIDATION_ERROR", "审批驳回必须填写理由（将留痕并退回起草人）");
        }
        a.setStatus(ReleaseAssessmentStatus.REJECTED);
        a.setRejectedAt(Instant.now());
        assessmentRepository.save(a);
        actionRepository.save(new ReleaseAssessmentAction(a.getId(), ReleaseAssessmentActionType.REJECT,
                user.userId(), user.realName(), req.reason(), ReleaseAssessmentStatus.REJECTED,
                "退回起草人修改后重新提交"));
        return toView(a);
    }

    /**
     * 执行解除（终态动作）：
     * 矫正档案状态机 → RELEASED；出具永久解除证明书编号；核销红点；清空并冻结实时位置。
     */
    @Transactional
    public ReleaseAssessmentView execute(Long id, ReleaseDecisionRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = load(id, user);
        ReleaseAssessmentStateMachine.assertTransition(a.getStatus(), ReleaseAssessmentStatus.DONE);
        CorrectionObject o = a.getOffender();

        if (caseRepository.existsByOffender_IdAndStatus(o.getId(), ViolationCaseStatus.REGISTERED)) {
            throw new ApiException("OPEN_CASE_EXISTS",
                    "该对象仍存在待处置违规案件，须先办结（训诫/收监/驳回/撤销）后才能执行解除");
        }

        // 走矫正档案状态机：非在矫/请假/训诫态会被 409 拦截
        StatusTransition st = transitionService.apply(o, CorrectionStatus.RELEASED,
                (req.reason() == null || req.reason().isBlank())
                        ? "矫正期满，评估审批通过，依法解除社区矫正" : req.reason(), user);

        String certNo = nextCertificateNo(o);
        Instant now = Instant.now();
        a.setStatus(ReleaseAssessmentStatus.DONE);
        a.setReleasedAt(now);
        a.setReleasedBy(user.userId());
        a.setReleasedByName(user.realName());
        a.setReleaseCertificateNo(certNo);
        assessmentRepository.save(a);

        // 永久解除标记落到对象档案
        o.setReleaseCertificateNo(certNo);
        o.setReleasedMarkedAt(now);
        // 清空实时位置并冻结：之后腕表/手机上报不再更新位置
        o.setLastLocationAt(null);
        o.setLastLat(null);
        o.setLastLng(null);
        o.setLastInsideFence(null);
        o.setLastForbidden(null);
        o.setLastBattery(null);
        o.setLastSignal(null);
        o.setLastWorn(null);
        o.setLocationFrozen(true);
        objectRepository.save(o);

        // 核销该对象全部未处置红点（解除后不再挂作战台）
        int redCleared = 0;
        for (ViolationEvent v : eventRepository.findByOffender_IdAndReadFlagFalse(o.getId())) {
            v.setReadFlag(true);
            eventRepository.save(v);
            redCleared++;
        }

        actionRepository.save(new ReleaseAssessmentAction(a.getId(), ReleaseAssessmentActionType.EXECUTE,
                user.userId(), user.realName(),
                (req.reason() == null || req.reason().isBlank())
                        ? "依法解除社区矫正，出具永久解除标记" : req.reason(),
                ReleaseAssessmentStatus.DONE,
                "出具解除证明书 " + certNo + "；矫正流转记录 #" + st.getId()
                        + "；核销未处置红点 " + redCleared + " 条；实时位置已清空并停止更新"));
        return toView(a);
    }

    // ---------------- 归档检索（解除后档案仍可按编号查） ----------------

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> archiveLookup(String correctionNo, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        String key = correctionNo == null ? "" : correctionNo.trim().toUpperCase();
        if (key.length() < 3) {
            throw new ApiException("VALIDATION_ERROR", "请输入至少 3 位矫正编号进行检索");
        }
        List<java.util.Map<String, Object>> hits = new ArrayList<>();
        for (CorrectionObject o : objectRepository.findByCorrectionNoContainingIgnoreCaseOrderByCorrectionNo(key)) {
            if (!canViewObject(o, user)) {
                continue;
            }
            Optional<ReleaseAssessment> done = assessmentRepository
                    .findByOffender_IdAndStatus(o.getId(), ReleaseAssessmentStatus.DONE);
            java.util.Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("objectId", o.getId());
            row.put("correctionNo", o.getCorrectionNo());
            row.put("maskedName", o.getMaskedName());
            row.put("officeName", o.getOffice().getName());
            row.put("timezone", o.getOffice().getTimezone());
            row.put("status", o.getStatus().name());
            row.put("statusLabel", o.getStatus().getLabel());
            row.put("endDate", String.valueOf(o.getEndDate()));
            row.put("released", o.getStatus() == CorrectionStatus.RELEASED);
            row.put("releaseCertificateNo", o.getReleaseCertificateNo() == null ? "" : o.getReleaseCertificateNo());
            row.put("releasedMarkedAt", o.getReleasedMarkedAt() == null ? "" : o.getReleasedMarkedAt());
            row.put("locationFrozen", o.isLocationFrozen());
            row.put("assessmentId", done.map(ReleaseAssessment::getId).orElse(0L));
            hits.add(row);
        }
        return java.util.Map.of("keyword", key, "count", hits.size(), "items", hits);
    }

    private boolean canViewObject(CorrectionObject o, LoginUser user) {
        try {
            accessControl.assertCanView(o, user);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    // ---------------- 装配 ----------------

    private ReleaseAssessment load(Long id, LoginUser user) {
        ReleaseAssessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("解除评估报告不存在（编号：" + id + "）"));
        accessControl.assertCanView(a.getOffender(), user);
        return a;
    }

    private ReleaseAssessmentView toView(ReleaseAssessment a) {
        CorrectionObject o = a.getOffender();
        List<String> allowed = switch (a.getStatus()) {
            case DRAFT -> List.of("SUBMIT", "UPDATE");
            case SUBMITTED -> List.of("APPROVE", "REJECT");
            case REJECTED -> List.of("UPDATE", "SUBMIT");
            case APPROVED -> List.of("EXECUTE");
            case DONE -> List.of();
        };
        return new ReleaseAssessmentView(
                a.getId(), a.getReportNo(),
                o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getId(), o.getOffice().getName(), o.getOffice().getTimezone(),
                a.getStatus().name(), a.getStatus().getLabel(), a.getDueDate(),
                a.getCheckinDayRate(), a.getKeyReportRate(), a.getTrackActiveDays(),
                a.getBreachCount30d(), a.getAdmonishCount(), a.getOpenViolationCase(),
                a.getConclusion(), conclusionLabel(a.getConclusion()), a.getOpinion(),
                a.getGeneratedByName(), a.getGeneratedAt(), a.getSubmittedAt(),
                a.getApprovedByName(), a.getApprovedAt(), a.getRejectedAt(),
                a.getReleasedAt(), a.getReleaseCertificateNo(), a.getReleasedByName(), allowed);
    }

    private void assertConclusion(String c) {
        if (!"SUGGEST_RELEASE".equals(c) && !"CONTINUE_EDUCATION".equals(c)) {
            throw new ApiException("VALIDATION_ERROR",
                    "评估结论非法：SUGGEST_RELEASE 建议按期解除 / CONTINUE_EDUCATION 建议延长教育");
        }
    }

    static String conclusionLabel(String c) {
        return switch (c == null ? "" : c) {
            case "SUGGEST_RELEASE" -> "建议按期解除";
            case "CONTINUE_EDUCATION" -> "建议延长教育";
            default -> "—";
        };
    }

    private String pct(Double v) {
        return v == null ? "—" : Math.round(v * 100) + "%";
    }

    private String nextReportNo() {
        long seq = assessmentRepository.count() + 1;
        String yy = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).format(NO_YEAR_FMT);
        return String.format("PG%s-%04d", yy, seq);
    }

    private String nextCertificateNo(CorrectionObject o) {
        String yy = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).format(NO_YEAR_FMT);
        return "JCS-JC" + yy + "-" + o.getCorrectionNo();
    }
}
