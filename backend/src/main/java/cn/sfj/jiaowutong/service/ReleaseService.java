package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.AssessmentFormRequest;
import cn.sfj.jiaowutong.web.vo.CompletionView;
import cn.sfj.jiaowutong.web.vo.ReleaseActionView;
import cn.sfj.jiaowutong.web.vo.ReleaseAssessmentView;
import cn.sfj.jiaowutong.web.vo.ReleaseDueItem;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 解除与评估：
 * 1. 到期清单：在管对象矫正期满（按其司法所时区的日历日）进入待办，给出评估提示；
 * 2. 到期对象先生成评估报告（近 30 天报到双口径自动带入，供评估参考）；
 * 3. 走解除状态机：草稿→提交→审批（通过/退回补正）→宣告解除；
 * 4. 宣告解除联动档案进入「解除」终态并打永久标记 releasedPermanently、签发解除证明书，
 *    关闭全部在办红点、清空实时位置——此后不再出现在在矫名单与作战台红点，定位数据不再更新，
 *    但档案仍按矫正编号可查（归档）。
 */
@Service
public class ReleaseService {

    /** 到期清单提前纳入的天数 */
    private static final long DUE_LOOKAHEAD_DAYS = 30;
    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Set<CorrectionStatus> RELEASABLE =
            EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED);

    public static final Map<String, String> ACTION_LABELS = Map.of(
            "DRAFT", "生成评估报告",
            "EDIT", "补正评估报告",
            "SUBMIT", "提交审批",
            "APPROVE", "审批通过",
            "RETURN", "退回补正",
            "DECLARE", "宣告解除");

    private final ReleaseAssessmentRepository assessmentRepository;
    private final ReleaseActionLogRepository logRepository;
    private final CorrectionObjectRepository objectRepository;
    private final AccessControlService accessControl;
    private final CompletionService completionService;
    private final ObjectService objectService;

    public ReleaseService(ReleaseAssessmentRepository assessmentRepository,
                          ReleaseActionLogRepository logRepository,
                          CorrectionObjectRepository objectRepository,
                          AccessControlService accessControl,
                          CompletionService completionService,
                          ObjectService objectService) {
        this.assessmentRepository = assessmentRepository;
        this.logRepository = logRepository;
        this.objectRepository = objectRepository;
        this.accessControl = accessControl;
        this.completionService = completionService;
        this.objectService = objectService;
    }

    // ---------------- 到期清单 ----------------

    @Transactional(readOnly = true)
    public List<ReleaseDueItem> dueList(LoginUser user) {
        List<CorrectionObject> scoped = accessControl.filterByScope(objectRepository.findAll(), user);
        return scoped.stream()
                .filter(o -> RELEASABLE.contains(o.getStatus()))
                .map(o -> {
                    ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
                    LocalDate localToday = Instant.now().atZone(zone).toLocalDate();
                    ReleaseAssessment open = openAssessment(o.getId()).orElse(null);
                    boolean inWindow = o.getEndDate() != null
                            && !o.getEndDate().isAfter(localToday.plusDays(DUE_LOOKAHEAD_DAYS));
                    return new java.util.AbstractMap.SimpleEntry<>(o, new Object[]{localToday, open, inWindow});
                })
                .filter(e -> {
                    Object[] ctx = e.getValue();
                    boolean inWindow = (boolean) ctx[2];
                    ReleaseAssessment open = (ReleaseAssessment) ctx[1];
                    // 期满窗口内，或已在走解除流程（即使提前），都纳入待办
                    return inWindow || open != null;
                })
                .map(e -> {
                    CorrectionObject o = e.getKey();
                    Object[] ctx = e.getValue();
                    LocalDate localToday = (LocalDate) ctx[0];
                    ReleaseAssessment open = (ReleaseAssessment) ctx[1];
                    long days = o.getEndDate() == null ? Long.MAX_VALUE
                            : java.time.temporal.ChronoUnit.DAYS.between(localToday, o.getEndDate());
                    return new ReleaseDueItem(
                            o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                            o.getOffice().getId(), o.getOffice().getName(), o.getOffice().getTimezone(),
                            o.getStatus().name(), o.getStatus().getLabel(),
                            o.getEndDate(), days, days < 0,
                            open != null,
                            open == null ? null : open.getStage().name(),
                            open == null ? null : open.getStage().getLabel(),
                            open == null ? null : open.getAssessmentNo());
                })
                .sorted(Comparator.comparing(ReleaseDueItem::daysUntilExpiry)
                        .thenComparing(ReleaseDueItem::correctionNo))
                .toList();
    }

    // ---------------- 评估报告 ----------------

    @Transactional
    public ReleaseAssessmentView generate(Long objectId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(objectId, user);
        if (!RELEASABLE.contains(o.getStatus())) {
            throw ApiException.badRequest("NOT_RELEASABLE",
                    "对象当前为「" + o.getStatus().getLabel() + "」，不在可办理解除评估的在管状态");
        }
        if (openAssessment(objectId).isPresent()) {
            throw ApiException.badRequest("ASSESSMENT_IN_PROGRESS",
                    "该对象已有一份未办结的解除评估报告，请在原报告上继续流程，不能重复发起");
        }

        ReleaseAssessment a = new ReleaseAssessment();
        a.setAssessmentNo(generateNo(o));
        a.setOffender(o);
        a.setStage(ReleaseStage.DRAFT);
        a.setEndDate(o.getEndDate());

        // 自动带入近 30 天考核双口径作为评估参考初稿，制作人可修改
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        CompletionView c = completionService.build(o, zone);
        int initScore = (int) Math.round((c.checkinDayRate() * 40 + c.keyReportRate() * 60) * 100);
        a.setScore(initScore);
        a.setCompliance("近 30 天打卡 " + c.actualCheckinDays() + "/" + c.calendarDays()
                + " 天；规定报到节点完成 " + c.keyReportDone() + "/" + c.keyReportDue()
                + "。当前档案状态「" + o.getStatus().getLabel() + "」。");
        a.setEducation("按司法所安排参加法治教育与社区学习（待制作人核实补全）。");
        a.setRepentance("认罪悔罪态度、思想汇报情况（待制作人谈话核实后补全）。");
        a.setRiskLevel(riskOf(initScore));
        a.setConclusion("综合考核评分 " + initScore + " 分，再犯罪风险初步评定为「"
                + riskLabel(riskOf(initScore)) + "」，请结合谈话与监管情况核定是否按期解除。");
        a.setAssessorName(user.realName());
        a.setAssessedAt(Instant.now());
        assessmentRepository.save(a);
        log(a, "DRAFT", ReleaseStage.DRAFT, user, "矫正期满生成解除矫正评估报告",
                "自动带入近 30 天报到双口径作为初稿");
        return detail(a.getId(), user);
    }

    /** 草稿/退回补正阶段保存评估内容。 */
    @Transactional
    public ReleaseAssessmentView saveDraft(Long id, AssessmentFormRequest form, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = loadVisible(id, user);
        if (a.getStage() != ReleaseStage.DRAFT && a.getStage() != ReleaseStage.REJECTED) {
            throw ApiException.badRequest("RELEASE_TRANSITION",
                    "评估已提交并在审批中，不能直接修改；如需更改请等待审批退回补正");
        }
        applyForm(a, form);
        a.setAssessorName(user.realName());
        a.setAssessedAt(Instant.now());
        assessmentRepository.save(a);
        if (a.getStage() == ReleaseStage.REJECTED) {
            log(a, "EDIT", ReleaseStage.REJECTED, user, "按退回意见补正评估报告", null);
        }
        return detail(id, user);
    }

    /** 提交审批（草稿或补正后）。 */
    @Transactional
    public ReleaseAssessmentView submit(Long id, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = loadVisible(id, user);
        ReleaseStateMachine.assertTransition(a.getStage(), ReleaseStage.SUBMITTED);
        if (a.getScore() == null || a.getConclusion() == null || a.getConclusion().isBlank()) {
            throw ApiException.badRequest("ASSESSMENT_INCOMPLETE",
                    "评估报告缺少综合评分或评估结论，请补全后再提交审批");
        }
        if (a.getRiskLevel() == null) {
            throw ApiException.badRequest("ASSESSMENT_INCOMPLETE", "请评定再犯罪风险等级后再提交");
        }
        a.setStage(ReleaseStage.SUBMITTED);
        a.setSubmittedBy(user.userId());
        a.setSubmittedByName(user.realName());
        a.setSubmittedAt(Instant.now());
        assessmentRepository.save(a);
        log(a, "SUBMIT", ReleaseStage.SUBMITTED, user, reason, "提交解除矫正审批");
        return detail(id, user);
    }

    /** 审批：approve=true 通过，false 退回补正（须填意见）。 */
    @Transactional
    public ReleaseAssessmentView decide(Long id, boolean approve, String opinion, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = loadVisible(id, user);
        if (opinion == null || opinion.trim().length() < 4) {
            throw ApiException.badRequest("OPINION_REQUIRED",
                    (approve ? "审批通过" : "退回补正") + "须填写不少于 4 字的意见并留痕");
        }
        if (approve) {
            ReleaseStateMachine.assertTransition(a.getStage(), ReleaseStage.APPROVED);
            a.setStage(ReleaseStage.APPROVED);
            a.setApprovedBy(user.userId());
            a.setApprovedByName(user.realName());
            a.setApprovedAt(Instant.now());
            a.setApprovalOpinion(opinion);
            assessmentRepository.save(a);
            log(a, "APPROVE", ReleaseStage.APPROVED, user, opinion, "审批通过，可宣告解除");
        } else {
            ReleaseStateMachine.assertTransition(a.getStage(), ReleaseStage.REJECTED);
            a.setStage(ReleaseStage.REJECTED);
            a.setApprovedBy(user.userId());
            a.setApprovedByName(user.realName());
            a.setApprovedAt(Instant.now());
            a.setApprovalOpinion(opinion);
            assessmentRepository.save(a);
            log(a, "RETURN", ReleaseStage.REJECTED, user, opinion, "审批退回，待补正后重新提交");
        }
        return detail(id, user);
    }

    /** 宣告解除：终态，打永久标记、冻结定位、关闭红点。 */
    @Transactional
    public ReleaseAssessmentView declare(Long id, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ReleaseAssessment a = loadVisible(id, user);
        CorrectionObject o = a.getOffender();
        ReleaseStateMachine.assertTransition(a.getStage(), ReleaseStage.DECLARED);

        // 档案状态机只允许「在矫」→解除；请假/训诫态须先销假/教育恢复，杜绝跨状态跳跃
        if (o.getStatus() != CorrectionStatus.SERVING) {
            throw new ApiException("INVALID_TRANSITION",
                    "宣告解除前档案须处于「在矫」：对象当前为「" + o.getStatus().getLabel()
                            + "」，请先在档案上办理销假返所或教育改正恢复在矫，再宣告解除");
        }
        if (reason == null || reason.trim().length() < 4) {
            throw ApiException.badRequest("OPINION_REQUIRED", "宣告解除须填写不少于 4 字的宣告意见并留痕");
        }

        // 联动档案状态机：在矫 → 解除（终态）。applyTransition 统一负责
        // 打永久标记、签发证明书、冻结实时定位、关闭在办红点。
        objectService.applyTransition(o, CorrectionStatus.RELEASED, reason,
                user.userId(), user.realName(), "解除评估 " + a.getAssessmentNo());

        Instant now = Instant.now();
        a.setStage(ReleaseStage.DECLARED);
        a.setDeclaredBy(user.userId());
        a.setDeclaredByName(user.realName());
        a.setDeclaredAt(now);
        a.setCertificateNo(o.getReleaseCertificateNo());
        assessmentRepository.save(a);
        log(a, "DECLARE", ReleaseStage.DECLARED, user, reason,
                "已宣告解除并打永久标记，签发解除证明书 " + o.getReleaseCertificateNo()
                        + "，实时定位停止更新、退出在矫名单与作战台红点，档案按编号归档可查");
        return detail(id, user);
    }

    // ---------------- 详情 ----------------

    /** 按对象取最新一份解除评估详情（含已宣告归档的），无则返回 null。供档案页入口使用。 */
    @Transactional(readOnly = true)
    public ReleaseAssessmentView latestForObject(Long objectId, LoginUser user) {
        CorrectionObject o = accessControl.loadVisible(objectId, user);
        return assessmentRepository.findByOffender_IdOrderByCreatedAtDescIdDesc(objectId).stream()
                .findFirst()
                .map(a -> toView(a, o))
                .orElse(null);
    }

    @Transactional(readOnly = true)
    public ReleaseAssessmentView detail(Long id, LoginUser user) {
        ReleaseAssessment a = loadVisible(id, user);
        return toView(a, a.getOffender());
    }

    private ReleaseAssessmentView toView(ReleaseAssessment a, CorrectionObject o) {
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        LocalDate localToday = Instant.now().atZone(zone).toLocalDate();
        long days = o.getEndDate() == null ? Long.MAX_VALUE
                : java.time.temporal.ChronoUnit.DAYS.between(localToday, o.getEndDate());

        List<ReleaseActionView> logs =
                logRepository.findByAssessmentIdOrderByOperatedAtAscIdAsc(a.getId()).stream()
                        .map(l -> new ReleaseActionView(l.getId(), l.getAction(),
                                ACTION_LABELS.getOrDefault(l.getAction(), l.getAction()),
                                l.getToStage(), stageLabel(l.getToStage()),
                                l.getOperatorName(), l.getReason(), l.getDetail(), l.getOperatedAt()))
                        .toList();

        return new ReleaseAssessmentView(
                a.getId(), a.getAssessmentNo(),
                o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getId(), o.getOffice().getName(), o.getOffice().getTimezone(),
                a.getStage().name(), a.getStage().getLabel(),
                a.getEndDate(), days, days < 0,
                a.getScore(), a.getEducation(), a.getCompliance(), a.getRepentance(),
                a.getRiskLevel(), a.getRiskLevel() == null ? null : riskLabel(a.getRiskLevel()),
                a.getConclusion(), a.getAssessorName(), a.getAssessedAt(),
                a.getSubmittedByName(), a.getSubmittedAt(),
                a.getApprovedByName(), a.getApprovedAt(), a.getApprovalOpinion(),
                a.getDeclaredByName(), a.getDeclaredAt(), a.getCertificateNo(),
                completionService.build(o, zone), logs);
    }

    // ---------------- 辅助 ----------------

    private ReleaseAssessment loadVisible(Long id, LoginUser user) {
        ReleaseAssessment a = assessmentRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("解除评估报告不存在（编号：" + id + "）"));
        accessControl.assertCanView(a.getOffender(), user);
        return a;
    }

    private Optional<ReleaseAssessment> openAssessment(Long offenderId) {
        return assessmentRepository
                .findFirstByOffender_IdAndStageNotOrderByIdDesc(offenderId, ReleaseStage.DECLARED);
    }

    private void applyForm(ReleaseAssessment a, AssessmentFormRequest f) {
        if (f.score() != null) a.setScore(f.score());
        if (f.education() != null) a.setEducation(f.education());
        if (f.compliance() != null a.setCompliance(f.compliance());
        if (f.repentance() != null) a.setRepentance(f.repentance());
        if (f.riskLevel() != null) {
            String risk = f.riskLevel().trim().toUpperCase();
            if (!risk.isBlank()) {
                if (!risk.equals("LOW") && !risk.equals("MEDIUM") && !risk.equals("HIGH")) {
                    throw ApiException.badRequest("VALIDATION_ERROR", "再犯罪风险等级只能是 LOW/MEDIUM/HIGH");
                }
                a.setRiskLevel(risk);
            }
        }
        if (f.conclusion() != null) a.setConclusion(f.conclusion());
    }

    private String generateNo(CorrectionObject o) {
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        String day = Instant.now().atZone(zone).format(NO_FMT);
        String prefix = "PG-" + day + "-";
        long seq = assessmentRepository.countByAssessmentNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    private String riskOf(int score) {
        if (score >= 80) return "LOW";
        if (score >= 60) return "MEDIUM";
        return "HIGH";
    }

    static String riskLabel(String level) {
        return switch (level) {
            case "LOW" -> "低风险";
            case "MEDIUM" -> "中风险";
            case "HIGH" -> "高风险";
            default -> level;
        };
    }

    private String stageLabel(String name) {
        try {
            return ReleaseStage.valueOf(name).getLabel();
        } catch (Exception e) {
            return name;
        }
    }

    private void log(ReleaseAssessment a, String action, ReleaseStage to,
                     LoginUser user, String reason, String detail) {
        logRepository.save(new ReleaseActionLog(a.getId(), action, to,
                user.userId(), user.realName(), reason, detail));
    }
}
