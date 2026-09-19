package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.dto.RegisterDisposalRequest;
import cn.sfj.jiaowutong.web.vo.DisposalActionView;
import cn.sfj.jiaowutong.web.vo.DisposalDetailView;
import cn.sfj.jiaowutong.web.vo.DisposalView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 违规处置：
 * 1. 登记：把越界/禁区/未报到等红点事件（或手工情况）合成可处置的处置单。同一对象同一事由在
 *    {@link ViolationEventService#DISPOSAL_MERGE_WINDOW_HOURS} 小时窗内只有一条“待处置”单，
 *    事件不断并入，绝不一晚刷出七八条；
 * 2. 处置：已登记后可「训诫」「收监」，联动档案状态机（非法去向由档案状态机拦截），
 *    也可「驳回」（经查不构成违规）或「撤销」（登记有误）；
 * 3. 留痕：每一步（登记/训诫/收监/驳回/撤销）都写操作人、理由、结论，只追加不改写，
 *    处置结论不能被直接修改——只能沿 {@link DisposalStateMachine} 推进。
 */
@Service
public class DisposalService {

    private static final DateTimeFormatter NO_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final DisposalRecordRepository disposalRepository;
    private final DisposalActionLogRepository logRepository;
    private final ViolationEventRepository violationRepository;
    private final CorrectionObjectRepository objectRepository;
    private final ViolationEventService violationEventService;
    private final ObjectService objectService;
    private final AccessControlService accessControl;

    public DisposalService(DisposalRecordRepository disposalRepository,
                           DisposalActionLogRepository logRepository,
                           ViolationEventRepository violationRepository,
                           CorrectionObjectRepository objectRepository,
                           ViolationEventService violationEventService,
                           ObjectService objectService,
                           AccessControlService accessControl) {
        this.disposalRepository = disposalRepository;
        this.logRepository = logRepository;
        this.violationRepository = violationRepository;
        this.objectRepository = objectRepository;
        this.violationEventService = violationEventService;
        this.objectService = objectService;
        this.accessControl = accessControl;
    }

    public static final Map<String, String> ACTION_LABELS = Map.of(
            "REGISTER", "登记受理",
            "REGISTER_APPEND", "补录并入",
            "ADMONISH", "予以训诫",
            "REIMPRISON", "提请收监",
            "REJECT", "驳回",
            "REVOKE", "撤销");

    // ---------------- 列表 ----------------

    @Transactional(readOnly = true)
    public List<DisposalView> list(DisposalStatus status, Long objectId, LoginUser user) {
        Set<Long> visibleIds = visibleObjectIds(user);
        return disposalRepository.findAll().stream()
                .filter(r -> visibleIds.contains(r.getOffender().getId()))
                .filter(r -> status == null || r.getStatus() == status)
                .filter(r -> objectId == null || objectId.equals(r.getOffender().getId()))
                .sorted(Comparator.comparing(DisposalRecord::getRegisteredAt)
                        .thenComparing(DisposalRecord::getId).reversed())
                .map(this::toView)
                .toList();
    }

    /** 待处置（已登记）数量，供作战台红点/角标使用。 */
    @Transactional(readOnly = true)
    public long pendingCount(LoginUser user) {
        Set<Long> visibleIds = visibleObjectIds(user);
        return disposalRepository.findAll().stream()
                .filter(r -> r.getStatus() == DisposalStatus.REGISTERED)
                .filter(r -> visibleIds.contains(r.getOffender().getId()))
                .count();
    }

    private Set<Long> visibleObjectIds(LoginUser user) {
        List<CorrectionObject> scoped = accessControl.filterByScope(objectRepository.findAll(), user);
        Set<Long> ids = new HashSet<>();
        scoped.forEach(o -> ids.add(o.getId()));
        return ids;
    }

    // ---------------- 详情 ----------------

    /** 处置中心“待登记受理”的红点：未挂处置单、未核销，按数据范围过滤。 */
    @Transactional(readOnly = true)
    public List<cn.sfj.jiaowutong.web.vo.DashboardView.RedDotItem> openEvents(LoginUser user) {
        Set<Long> visibleIds = visibleObjectIds(user);
        return violationRepository
                .findByDisposalIsNullAndReadFlagFalseOrderByEventTimeDescIdDesc().stream()
                .filter(e -> visibleIds.contains(e.getOffender().getId()))
                .map(e -> ObjectService.toRedDot(e, e.getOffender()))
                .toList();
    }

    @Transactional(readOnly = true)
    public DisposalDetailView detail(Long id, LoginUser user) {
        DisposalRecord r = loadVisible(id, user);
        CorrectionObject o = r.getOffender();

        List<DisposalDetailView.LinkedEventView> events =
                violationRepository.findByDisposal_Id(id).stream()
                        .sorted(Comparator.comparing(ViolationEvent::getEventTime))
                        .map(e -> new DisposalDetailView.LinkedEventView(
                                e.getId(), e.getType(), ObjectService.typeLabel(e.getType()),
                                e.getDetail(), e.getEventTime(), e.getReadFlag()))
                        .toList();

        List<DisposalActionView> logs =
                logRepository.findByDisposalIdOrderByOperatedAtAscIdAsc(id).stream()
                        .map(l -> new DisposalActionView(l.getId(), l.getAction(),
                                ACTION_LABELS.getOrDefault(l.getAction(), l.getAction()),
                                l.getToStatus(), labelStatus(l.getToStatus()),
                                l.getOperatorName(), l.getReason(), l.getDetail(), l.getOperatedAt()))
                        .toList();

        return new DisposalDetailView(toView(r), o.getStatus().name(), o.getStatus().getLabel(),
                events, logs);
    }

    // ---------------- 登记（红点事件 → 处置单，时间窗合并） ----------------

    /**
     * 把指定（或该对象该事由全部未挂单）红点事件登记为处置单。
     * 时间窗内已存在待处置单时并入该单并补一条留痕，不另开新单。
     */
    @Transactional
    public DisposalDetailView registerFromEvents(Long objectId, DisposalCategory category,
                                                 List<Long> eventIds, String title,
                                                 String detail, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        assertReason(reason);
        CorrectionObject o = accessControl.loadVisible(objectId, user);

        List<ViolationEvent> candidates = violationRepository
                .findByOffender_IdAndDisposalIsNullOrderByEventTimeDescIdDesc(objectId).stream()
                .filter(e -> DisposalCategory.fromEventType(e.getType()) == category)
                .filter(e -> eventIds == null || eventIds.isEmpty() || eventIds.contains(e.getId()))
                .toList();
        if (candidates.isEmpty()) {
            throw ApiException.badRequest("NO_EVENT_TO_REGISTER",
                    "该对象当前没有可登记的「" + category.getLabel() + "」未处置事件，可能已被其他处置单受理");
        }

        DisposalRecord record = findOpenInWindow(objectId, category).orElse(null);
        boolean isNew = record == null;
        if (isNew) {
            record = new DisposalRecord();
            record.setDisposalNo(generateNo(o));
            record.setOffender(o);
            record.setCategory(category);
            record.setStatus(DisposalStatus.REGISTERED);
            record.setSource("AUTO");
            record.setTitle(blankTo(title, defaultTitle(o, category)));
            record.setDetail(detail);
            record.setRegisteredBy(user.userId());
            record.setRegisteredByName(user.realName());
            record.setRegisterReason(reason);
            record.setRegisteredAt(Instant.now());
            record.setEventCount(0);
            disposalRepository.save(record);
        }

        attachEvents(record, candidates);
        if (detail != null && !detail.isBlank()) {
            record.setDetail(mergeNote(record.getDetail(), detail));
        }
        disposalRepository.save(record);

        if (isNew) {
            log(record, "REGISTER", DisposalStatus.REGISTERED, user, reason,
                    "由 " + candidates.size() + " 条红点事件登记受理（来源：事件合成）");
        } else {
            log(record, "REGISTER_APPEND", DisposalStatus.REGISTERED, user, reason,
                    "时间窗内并入既有待处置单，追加 " + candidates.size() + " 条事件");
        }
        return detail(record.getId(), user);
    }

    /** 手工登记（无红点事件，如群众举报、当面核查发现的违规）。 */
    @Transactional
    public DisposalDetailView registerManual(RegisterDisposalRequest req, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        assertReason(req.reason());
        CorrectionObject o = accessControl.loadVisible(req.objectId(), user);
        DisposalCategory category = parseCategory(req.category());

        DisposalRecord record = findOpenInWindow(o.getId(), category).orElse(null);
        if (record != null) {
            // 同一对象同一事由时间窗内已有待处置单：补登情况并入，不新开单
            if (req.detail() != null && !req.detail().isBlank()) {
                record.setDetail(mergeNote(record.getDetail(), req.detail()));
            }
            disposalRepository.save(record);
            log(record, "REGISTER_APPEND", DisposalStatus.REGISTERED, user, req.reason(),
                    "手工补登情况并入既有待处置单（" + blankTo(req.title(), category.getLabel()) + "）");
            return detail(record.getId(), user);
        }

        record = new DisposalRecord();
        record.setDisposalNo(generateNo(o));
        record.setOffender(o);
        record.setCategory(category);
        record.setStatus(DisposalStatus.REGISTERED);
        record.setSource("MANUAL");
        record.setTitle(blankTo(req.title(), defaultTitle(o, category)));
        record.setDetail(req.detail());
        record.setRegisteredBy(user.userId());
        record.setRegisteredByName(user.realName());
        record.setRegisterReason(req.reason());
        record.setRegisteredAt(Instant.now());
        record.setEventCount(0);
        disposalRepository.save(record);
        log(record, "REGISTER", DisposalStatus.REGISTERED, user, req.reason(), "手工登记受理");
        return detail(record.getId(), user);
    }

    /** 把事件挂到处置单：受理后红点从作战台“未处置”移除，转入处置中心待处置。 */
    private void attachEvents(DisposalRecord record, List<ViolationEvent> events) {
        Instant first = record.getFirstEventAt();
        Instant last = record.getLastEventAt();
        int added = 0;
        for (ViolationEvent e : events) {
            if (e.getDisposal() != null && e.getDisposal().getId().equals(record.getId())) {
                continue;
            }
            e.setDisposal(record);
            e.setReadFlag(true);
            violationRepository.save(e);
            added++;
            if (first == null || e.getEventTime().isBefore(first)) first = e.getEventTime();
            if (last == null || e.getEventTime().isAfter(last)) last = e.getEventTime();
        }
        record.setEventCount(record.getEventCount() + added);
        record.setFirstEventAt(first);
        record.setLastEventAt(last);
    }

    // ---------------- 处置推进 ----------------

    @Transactional
    public DisposalDetailView advance(Long id, String action, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        if (reason == null || reason.isBlank()) {
            throw ApiException.badRequest("REASON_REQUIRED", "处置措施必须填写理由并留痕（不少于 4 字）");
        }
        DisposalRecord r = loadVisible(id, user);
        CorrectionObject o = r.getOffender();

        switch (action) {
            case "ADMONISH" -> {
                // 先校验处置状态机（已办结则在改动档案前拒绝），再联动档案状态机
                DisposalStateMachine.assertTransition(r.getStatus(), DisposalStatus.ADMONISHED);
                objectService.applyTransition(o, CorrectionStatus.ADMONISHED, reason,
                        user.userId(), user.realName(), "违规处置单 " + r.getDisposalNo());
                finish(r, DisposalStatus.ADMONISHED);
                int n = violationEventService.resolveEventsOfDisposal(id);
                log(r, action, DisposalStatus.ADMONISHED, user, reason,
                        "已联动档案状态变更为「训诫」，核销红点 " + n + " 条");
            }
            case "REIMPRISON" -> {
                DisposalStateMachine.assertTransition(r.getStatus(), DisposalStatus.REIMPRISONED);
                objectService.applyTransition(o, CorrectionStatus.REIMPRISONED, reason,
                        user.userId(), user.realName(), "违规处置单 " + r.getDisposalNo());
                finish(r, DisposalStatus.REIMPRISONED);
                int n = violationEventService.resolveEventsOfDisposal(id);
                log(r, action, DisposalStatus.REIMPRISONED, user, reason,
                        "已联动档案状态变更为「收监」（终态），核销红点 " + n + " 条");
            }
            case "REJECT" -> {
                // 驳回：经查不构成违规。处置单办结，原红点确认为无效并核销，不再回到作战台
                DisposalStateMachine.assertTransition(r.getStatus(), DisposalStatus.REJECTED);
                finish(r, DisposalStatus.REJECTED);
                int n = violationEventService.resolveEventsOfDisposal(id);
                log(r, action, DisposalStatus.REJECTED, user, reason,
                        "经查不构成违规，驳回处置，核销红点 " + n + " 条");
            }
            case "REVOKE" -> {
                // 撤销：登记有误。处置单办结，原红点摘回重新挂空，回到作战台待重新处置
                DisposalStateMachine.assertTransition(r.getStatus(), DisposalStatus.REVOKED);
                finish(r, DisposalStatus.REVOKED);
                int n = violationEventService.detachEventsOfDisposal(id);
                log(r, action, DisposalStatus.REVOKED, user, reason,
                        "登记有误，撤销处置单，原 " + n + " 条红点退回作战台");
            }
            default -> throw ApiException.badRequest("UNSUPPORTED_ACTION",
                    "不支持的处置动作：" + action);
        }
        disposalRepository.save(r);
        return detail(id, user);
    }

    private void finish(DisposalRecord r, DisposalStatus to) {
        r.setStatus(to);
        r.setResolvedAt(Instant.now());
    }

    private void log(DisposalRecord r, String action, DisposalStatus to,
                     LoginUser user, String reason, String detail) {
        logRepository.save(new DisposalActionLog(r.getId(), action, to,
                user.userId(), user.realName(), reason == null ? "" : reason, detail));
    }

    // ---------------- 辅助 ----------------

    private DisposalRecord loadVisible(Long id, LoginUser user) {
        DisposalRecord r = disposalRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("违规处置单不存在（编号：" + id + "）"));
        accessControl.assertCanView(r.getOffender(), user);
        return r;
    }

    private Optional<DisposalRecord> findOpenInWindow(Long offenderId, DisposalCategory category) {
        return disposalRepository
                .findFirstByOffender_IdAndCategoryAndStatusAndRegisteredAtAfterOrderByRegisteredAtDescIdDesc(
                        offenderId, category, DisposalStatus.REGISTERED,
                        Instant.now().minusSeconds(ViolationEventService.DISPOSAL_MERGE_WINDOW_HOURS * 3600));
    }

    private String generateNo(CorrectionObject o) {
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        String day = Instant.now().atZone(zone).format(NO_FMT);
        String prefix = "CC-" + day + "-";
        long seq = disposalRepository.countByDisposalNoStartingWith(prefix) + 1;
        return prefix + String.format("%04d", seq);
    }

    private DisposalView toView(DisposalRecord r) {
        CorrectionObject o = r.getOffender();
        JudicialOffice office = o.getOffice();
        return DisposalDetailView.toView(r, o.getCorrectionNo(), o.getMaskedName(),
                office.getName(), office.getTimezone());
    }

    private void assertReason(String reason) {
        if (reason == null || reason.trim().length() < 4) {
            throw ApiException.badRequest("REASON_REQUIRED", "操作理由不少于 4 个字，该步骤将全程留痕");
        }
    }

    private DisposalCategory parseCategory(String raw) {
        try {
            return DisposalCategory.valueOf(raw);
        } catch (Exception e) {
            throw ApiException.badRequest("VALIDATION_ERROR", "违规事由非法：" + raw);
        }
    }

    private String defaultTitle(CorrectionObject o, DisposalCategory category) {
        return o.getMaskedName() + "·" + category.getLabel() + "违规处置";
    }

    private String blankTo(String s, String fallback) {
        return s == null || s.isBlank() ? fallback : s;
    }

    private String mergeNote(String oldNote, String added) {
        if (oldNote == null || oldNote.isBlank()) {
            return added;
        }
        return oldNote + "；" + added;
    }

    private String labelStatus(String name) {
        try {
            return DisposalStatus.valueOf(name).getLabel();
        } catch (Exception e) {
            return name;
        }
    }
}
