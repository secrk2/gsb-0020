package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.RegisterCaseRequest;
import cn.sfj.jiaowutong.web.vo.DashboardView;
import cn.sfj.jiaowutong.web.vo.ViolationCaseView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 违规处置：
 * 1. 登记受理：把越界/禁区/未报到等原始预警（ViolationEvent）登记为可处置案件；
 *    同一对象、同一事由在 {@link #MERGE_WINDOW_HOURS} 小时时间窗内只立一条案件，
 *    窗内同类原始预警自动并入，避免一晚上刷出七八条；
 * 2. 处置：登记后只能「训诫 / 收监 / 驳回 / 撤销」，由 {@link ViolationCaseStateMachine}
 *    裁决；训诫、收监同时驱动矫正档案状态机，非法状态跳转 409 拦截；
 * 3. 留痕：每一步（含并入）追加 ViolationCaseAction，记录操作人、理由、时间与结论，
 *    案件状态字段不允许被直接修改；
 * 4. 红点核销：并入案件的原始预警标记已处置，作战台不再出现红点。
 */
@Service
public class ViolationCaseService {

    /** 同一对象同一事由的立案合并时间窗（小时） */
    public static final long MERGE_WINDOW_HOURS = 24;

    private static final Set<String> REASON_TYPES =
            Set.of("GEOFENCE_BREACH", "FORBIDDEN_ZONE", "ABSENT", "ADMONISH", "OTHER");
    private static final DateTimeFormatter NO_YEAR_FMT = DateTimeFormatter.ofPattern("yy");

    private final ViolationCaseRepository caseRepository;
    private final ViolationCaseActionRepository actionRepository;
    private final ViolationEventRepository eventRepository;
    private final AccessControlService accessControl;
    private final CorrectionTransitionService transitionService;

    public ViolationCaseService(ViolationCaseRepository caseRepository,
                                ViolationCaseActionRepository actionRepository,
                                ViolationEventRepository eventRepository,
                                AccessControlService accessControl,
                                CorrectionTransitionService transitionService) {
        this.caseRepository = caseRepository;
        this.actionRepository = actionRepository;
        this.eventRepository = eventRepository;
        this.accessControl = accessControl;
        this.transitionService = transitionService;
    }

    // ---------------- 列表 / 详情 ----------------

    @Transactional(readOnly = true)
    public List<ViolationCaseView> list(ViolationCaseStatus status, Long officeId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        return caseRepository.findAllByOrderByRegisteredAtDescIdDesc().stream()
                .filter(c -> status == null || c.getStatus() == status)
                .filter(c -> officeId == null || officeId.equals(c.getOfficeId()))
                .filter(c -> canView(c, user))
                .map(this::toView)
                .toList();
    }

    private boolean canView(ViolationCase c, LoginUser user) {
        try {
            accessControl.assertCanView(c.getOffender(), user);
            return true;
        } catch (ApiException e) {
            return false;
        }
    }

    /** 对象档案详情：该对象全部违规处置案件（最新在前），已做数据范围校验 */
    @Transactional(readOnly = true)
    public List<ViolationCaseView> viewsForOffender(Long offenderId, LoginUser user) {
        return caseRepository.findByOffender_IdOrderByRegisteredAtDescIdDesc(offenderId).stream()
                .filter(c -> canView(c, user))
                .map(this::toView)
                .toList();
    }

    @Transactional(readOnly = true)
    public ViolationCaseView.DetailView detail(Long caseId, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ViolationCase c = load(caseId, user);
        CorrectionObject o = c.getOffender();

        List<ViolationCaseView.ActionView> actions =
                actionRepository.findByCaseIdOrderByCreatedAtAscIdAsc(caseId).stream()
                        .map(a -> new ViolationCaseView.ActionView(
                                a.getId(), a.getAction().name(), a.getAction().getLabel(),
                                a.getOperatorName(), a.getReason(),
                                a.getStatusAfter().name(), a.getStatusAfter().getLabel(),
                                a.getDetail(), a.getCreatedAt()))
                        .toList();

        List<DashboardView.RedDotItem> events = eventRepository.findByCaseIdOrderByEventTimeDescIdDesc(caseId)
                .stream()
                .map(v -> ObjectService.toRedDot(v, o))
                        .toList();

        List<String> allowed = c.getStatus() == ViolationCaseStatus.REGISTERED
                ? List.of("ADMONISH", "REIMPRISON", "REJECT", "REVOKE")
                : List.of();

        return new ViolationCaseView.DetailView(toView(c), actions, events,
                o.getStatus().name(), o.getStatus().getLabel(), allowed);
    }

    // ---------------- 登记受理（时间窗合并） ----------------

    @Transactional
    public ViolationCaseView register(RegisterCaseRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(req.offenderId(), user);
        if (o.getStatus() == CorrectionStatus.RELEASED || o.getStatus() == CorrectionStatus.REIMPRISONED) {
            throw new ApiException("INVALID_ACTION",
                    "对象当前为「" + o.getStatus().getLabel() + "」，矫正流程已终结，不能再登记违规处置案件");
        }
        String manualReason = req.reason() == null || req.reason().isBlank() ? null : req.reason();
        if (manualReason != null && manualReason.length() < 4) {
            throw new ApiException("VALIDATION_ERROR", "登记事由不少于 4 个字（不填则由系统生成摘要）");
        }

        ViolationEvent anchor = null;
        if (req.eventId() != null) {
            anchor = eventRepository.findById(req.eventId())
                    .orElseThrow(() -> ApiException.notFound("原始预警不存在或已被清理"));
            if (!anchor.getOffender().getId().equals(o.getId())) {
                throw ApiException.badRequest("INVALID_ACTION", "该预警不属于所选对象，不能登记");
            }
            if (anchor.getCaseId() != null) {
                ViolationCase existed = caseRepository.findById(anchor.getCaseId()).orElse(null);
                throw ApiException.badRequest("ALREADY_REGISTERED",
                        "该预警已登记到案件「" + (existed == null ? anchor.getCaseId() : existed.getCaseNo())
                                + "」，同一时间窗内不重复立案");
            }
        }

        // 由红点登记时事由以预警类型为准；人工登记时校验类型
        String reasonType = anchor != null ? anchor.getType() : normalizeReasonType(req.reasonType());

        // 时间窗合并：同一对象同一事由已有待处置案件且在时间窗内，并入而不是另立
        Instant windowFloor = Instant.now().minusSeconds(MERGE_WINDOW_HOURS * 3600);
        ViolationCase open = caseRepository
                .findTopByOffender_IdAndReasonTypeAndStatusOrderByRegisteredAtDesc(
                        o.getId(), reasonType, ViolationCaseStatus.REGISTERED);
        if (open != null && open.getRegisteredAt().isAfter(windowFloor)) {
            attachEvents(open, anchor);
            actionRepository.save(new ViolationCaseAction(open.getId(), ViolationActionType.REGISTER,
                    user.userId(), user.realName(),
                    "时间窗内同类预警并入（不另立案件）：" + (anchor == null ? "人工补登" : anchor.getType())
                            + (manualReason == null ? "" : "；" + manualReason),
                    ViolationCaseStatus.REGISTERED, "合并立案"));
            return toView(open);
        }

        String summary = manualReason != null
                ? manualReason
                : reasonLabel(reasonType) + "预警登记处置";
        ViolationCase c = new ViolationCase(nextCaseNo(), o, reasonType, summary,
                user.userId(), user.realName());
        caseRepository.save(c);
        actionRepository.save(new ViolationCaseAction(c.getId(), ViolationActionType.REGISTER,
                user.userId(), user.realName(), summary, ViolationCaseStatus.REGISTERED,
                anchor == null ? "人工登记（无原始红点）" : "由预警 #" + anchor.getId() + " 登记"));
        attachEvents(c, anchor);
        return toView(c);
    }

    /** 把锚点预警与时间窗内同类未登记预警并入案件，原红点核销 */
    private void attachEvents(ViolationCase c, ViolationEvent anchor) {
        List<ViolationEvent> inWindow = eventRepository
                .findByOffender_IdAndTypeAndCaseIdIsNullAndEventTimeAfterOrderByEventTimeDescIdDesc(
                        c.getOffender().getId(), c.getReasonType(),
                        c.getRegisteredAt().minusSeconds(MERGE_WINDOW_HOURS * 3600));
        for (ViolationEvent v : inWindow) {
            v.setCaseId(c.getId());
            v.setReadFlag(true);
            eventRepository.save(v);
        }
        // 锚点可能早于时间窗下界（如登记了一条较早的红点），仍显式挂入
        if (anchor != null && anchor.getCaseId() == null) {
            anchor.setCaseId(c.getId());
            anchor.setReadFlag(true);
            eventRepository.save(anchor);
        }
    }

    // ---------------- 处置动作 ----------------

    @Transactional
    public ViolationCaseView act(Long caseId, ViolationActionType type, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        ViolationCase c = load(caseId, user);
        CorrectionObject o = c.getOffender();

        ViolationCaseStatus target = switch (type) {
            case REGISTER -> throw ApiException.badRequest("INVALID_ACTION",
                    "登记动作由登记接口产生，不能作为处置动作提交");
            case ADMONISH -> ViolationCaseStatus.ADMONISHED;
            case REIMPRISON -> ViolationCaseStatus.REIMPRISONED;
            case REJECT -> ViolationCaseStatus.REJECTED;
            case REVOKE -> ViolationCaseStatus.REVOKED;
        };
        ViolationCaseStateMachine.assertAction(c.getStatus(), target);

        String detail;
        if (type == ViolationActionType.ADMONISH) {
            // 走矫正档案状态机：非法当前状态（入矫登记/终态等）由状态机 409 拦截并说明原因
            StatusTransition st = transitionService.apply(o, CorrectionStatus.ADMONISHED, reason, user);
            detail = "矫正档案状态已由状态机流转为「训诫」，流转记录 #" + st.getId();
        } else if (type == ViolationActionType.REIMPRISON) {
            StatusTransition st = transitionService.apply(o, CorrectionStatus.REIMPRISONED, reason, user);
            detail = "矫正档案状态已由状态机流转为「收监」（终态），流转记录 #" + st.getId();
        } else if (type == ViolationActionType.REJECT) {
            detail = "预警不予处置，案件驳回，所附红点全部核销";
        } else {
            detail = "案件撤销，所附红点全部核销";
        }

        c.setStatus(target);
        c.setClosedAt(Instant.now());
        caseRepository.save(c);
        actionRepository.save(new ViolationCaseAction(c.getId(), type,
                user.userId(), user.realName(), reason, target, detail));

        // 终态处置后，案件所附原始预警全部核销（驳回/撤销也不再挂作战台红点）
        for (ViolationEvent v : eventRepository.findByCaseIdOrderByEventTimeDescIdDesc(c.getId())) {
            if (Boolean.FALSE.equals(v.getReadFlag())) {
                v.setReadFlag(true);
                eventRepository.save(v);
            }
        }
        return toView(c);
    }

    // ---------------- 新预警挂接（供轨迹上报/作战台调用） ----------------

    public enum EventAttach {
        /** 无开放案件、无同类未处置红点：作为新红点保存 */
        NEW_RED,
        /** 已并入待处置案件：保存为挂案留痕但不产生红点 */
        ATTACHED,
        /** 同类红点已存在或案件窗内已并入过：本条抑制不落库，避免刷屏 */
        SUPPRESSED
    }

    /**
     * 新的原始预警产生时调用：
     * 1. 同事由待处置案件在时间窗内：首条挂入案件（ATTACHED，不产生红点），其后全部 SUPPRESSED；
     * 2. 无案件但已有同类未处置红点：SUPPRESSED（红点边沿语义，同一事件不重复报警）；
     * 3. 其余：NEW_RED。
     */
    @Transactional
    public EventAttach attachNewEvent(ViolationEvent event) {
        ViolationCase open = caseRepository
                .findTopByOffender_IdAndReasonTypeAndStatusOrderByRegisteredAtDesc(
                        event.getOffender().getId(), event.getType(), ViolationCaseStatus.REGISTERED);
        if (open != null
                && open.getRegisteredAt().isAfter(Instant.now().minusSeconds(MERGE_WINDOW_HOURS * 3600))) {
            boolean attachedAfterRegister = eventRepository
                    .findByCaseIdOrderByEventTimeDescIdDesc(open.getId()).stream()
                    .anyMatch(v -> v.getType().equals(event.getType())
                            && !v.getEventTime().isBefore(open.getRegisteredAt()));
            if (attachedAfterRegister) {
                return EventAttach.SUPPRESSED;
            }
            event.setCaseId(open.getId());
            event.setReadFlag(true);
            return EventAttach.ATTACHED;
        }
        if (eventRepository.existsByOffender_IdAndTypeAndReadFlagFalse(
                event.getOffender().getId(), event.getType())) {
            return EventAttach.SUPPRESSED;
        }
        return EventAttach.NEW_RED;
    }

    // ---------------- 装配 ----------------

    private ViolationCase load(Long caseId, LoginUser user) {
        ViolationCase c = caseRepository.findById(caseId)
                .orElseThrow(() -> ApiException.notFound("违规处置案件不存在（编号：" + caseId + "）"));
        accessControl.assertCanView(c.getOffender(), user);
        return c;
    }

    private ViolationCaseView toView(ViolationCase c) {
        CorrectionObject o = c.getOffender();
        JudicialOffice office = o.getOffice();
        int merged = eventRepository.findByCaseIdOrderByEventTimeDescIdDesc(c.getId()).size();
        return new ViolationCaseView(
                c.getId(), c.getCaseNo(),
                o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                office.getId(), office.getName(), office.getTimezone(),
                c.getReasonType(), reasonLabel(c.getReasonType()), c.getSummary(),
                c.getStatus().name(), c.getStatus().getLabel(),
                c.getRegisteredAt(), c.getRegisteredByName(), c.getClosedAt(), merged);
    }

    private String normalizeReasonType(String raw) {
        String t = raw == null ? "" : raw.trim().toUpperCase();
        if (!REASON_TYPES.contains(t)) {
            throw ApiException.badRequest("VALIDATION_ERROR",
                    "违规事由类型非法，允许：GEOFENCE_BREACH 越界 / FORBIDDEN_ZONE 禁区闯入 / ABSENT 未报到 / OTHER 其他");
        }
        return t;
    }

    static String reasonLabel(String type) {
        return switch (type) {
            case "GEOFENCE_BREACH" -> "越界";
            case "FORBIDDEN_ZONE" -> "禁区闯入";
            case "ABSENT" -> "未按日报到";
            case "ADMONISH" -> "训诫";
            case "OTHER" -> "其他违规";
            default -> type;
        };
    }

    private String nextCaseNo() {
        long seq = caseRepository.count() + 1;
        String yy = Instant.now().atZone(ZoneId.of("Asia/Shanghai")).format(NO_YEAR_FMT);
        return String.format("AJ%s-%04d", yy, seq);
    }
}
