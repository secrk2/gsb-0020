package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.vo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

@Service
public class ObjectService {

    private final CorrectionObjectRepository objectRepository;
    private final StatusTransitionRepository transitionRepository;
    private final NameViewAuditRepository nameAuditRepository;
    private final ViolationEventRepository violationRepository;
    private final TrackPointRepository trackPointRepository;
    private final CheckInRepository checkInRepository;
    private final AccessControlService accessControl;
    private final ViolationCaseService violationCaseService;
    private final ReleaseAssessmentService releaseAssessmentService;
    private final CorrectionTransitionService transitionService;

    public ObjectService(CorrectionObjectRepository objectRepository,
                         StatusTransitionRepository transitionRepository,
                         NameViewAuditRepository nameAuditRepository,
                         ViolationEventRepository violationRepository,
                         TrackPointRepository trackPointRepository,
                         CheckInRepository checkInRepository,
                         AccessControlService accessControl,
                         ViolationCaseService violationCaseService,
                         ReleaseAssessmentService releaseAssessmentService,
                         CorrectionTransitionService transitionService) {
        this.objectRepository = objectRepository;
        this.transitionRepository = transitionRepository;
        this.nameAuditRepository = nameAuditRepository;
        this.violationRepository = violationRepository;
        this.trackPointRepository = trackPointRepository;
        this.checkInRepository = checkInRepository;
        this.accessControl = accessControl;
        this.violationCaseService = violationCaseService;
        this.releaseAssessmentService = releaseAssessmentService;
        this.transitionService = transitionService;
    }

    @Transactional(readOnly = true)
    public List<ObjectView> list(CorrectionStatus status, Long officeId,
                                 String keyword, boolean activeOnly, LoginUser user) {
        List<CorrectionObject> source;
        String key = keyword == null ? "" : keyword.trim().toUpperCase();
        if (!key.isEmpty()) {
            // 档案按编号可检索：解除归档对象也能按矫正编号查到
            source = objectRepository.findByCorrectionNoContainingIgnoreCaseOrderByCorrectionNo(key);
        } else {
            source = objectRepository.findAll();
        }
        return accessControl.filterByScope(source, user).stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> officeId == null || officeId.equals(o.getOffice().getId()))
                .filter(o -> !activeOnly || java.util.EnumSet.of(
                        CorrectionStatus.SERVING, CorrectionStatus.LEAVE,
                        CorrectionStatus.ADMONISHED).contains(o.getStatus()))
                .sorted(Comparator.comparing(CorrectionObject::getCorrectionNo))
                .map(o -> ObjectView.of(o, isSelfOffender(o, user)))
                .toList();
    }

    private boolean isSelfOffender(CorrectionObject o, LoginUser user) {
        return user.role() == Role.OFFENDER && o.getId().equals(user.offenderId());
    }

    @Transactional(readOnly = true)
    public ObjectDetailView detail(Long id, LoginUser user) {
        CorrectionObject o = accessControl.loadVisible(id, user);
        boolean self = isSelfOffender(o, user);
        ObjectView base = ObjectView.of(o, self);

        List<TransitionView> transitions = transitionRepository
                .findByOffenderIdOrderByOperatedAtDescIdDesc(id).stream()
                .limit(20)
                .map(t -> new TransitionView(
                        t.getFromStatus(),
                        t.getFromStatus() == null ? "—" : labelOf(t.getFromStatus()),
                        t.getToStatus(), labelOf(t.getToStatus()),
                        t.getReason(), t.getOperatorName(), t.getOperatedAt()))
                .toList();

        List<DashboardView.RedDotItem> violations = violationRepository
                .findTop20ByOffender_IdOrderByEventTimeDesc(id).stream()
                .map(v -> toRedDot(v, o)).toList();

        // “今日”按对象所在司法所时区
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        LocalDate localToday = Instant.now().atZone(zone).toLocalDate();
        boolean checkedToday = checkInRepository.existsByOffender_IdAndCheckDate(id, localToday);
        long trackCount = trackPointRepository
                .countByOffender_IdAndResult(id, TrackPoint.IngestResult.ACCEPTED);

        List<ViolationCaseView> cases = violationCaseService.viewsForOffender(id, user);
        ReleaseAssessmentView assessment = releaseAssessmentService.latestViewForOffender(id, user);

        return new ObjectDetailView(base, transitions, violations, checkedToday, trackCount,
                cases, assessment);
    }

    /** 二次确认 + 必填理由后返回全名，并落审计留痕 */
    @Transactional
    public String revealFullName(Long id, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(id, user);
        nameAuditRepository.save(new NameViewAudit(id, user.userId(), user.realName(), reason));
        return o.getFullName();
    }

    @Transactional(readOnly = true)
    public List<NameAuditView> nameAudits(Long id, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        accessControl.loadVisible(id, user);
        return nameAuditRepository.findByOffenderIdOrderByViewedAtDescIdDesc(id).stream()
                .map(a -> new NameAuditView(a.getViewerName(), a.getReason(), a.getViewedAt()))
                .toList();
    }

    /** 状态机流转；非法回退由状态机抛 INVALID_TRANSITION 说明原因。结论不允许直接改。 */
    @Transactional
    public TransitionView transition(Long id, CorrectionStatus target, String reason, LoginUser user) {
        accessControl.assertStaffOrSupervisor(user);
        CorrectionObject o = accessControl.loadVisible(id, user);
        CorrectionStatus from = o.getStatus();

        StatusTransition st = transitionService.apply(o, target, reason, user);

        // 训诫本身是处置措施（处置结论），同步留一条训诫记录但不作为待处置红点
        if (target == CorrectionStatus.ADMONISHED) {
            ViolationEvent record = new ViolationEvent(o, "ADMONISH",
                    "对象 " + o.getMaskedName() + " 因违规被训诫" + (reason == null || reason.isBlank() ? "" : "：" + reason),
                    Instant.now());
            record.setReadFlag(true);
            violationRepository.save(record);
        }

        return new TransitionView(from.name(), from.getLabel(),
                target.name(), target.getLabel(), reason, user.realName(), st.getOperatedAt());
    }

    /** 轨迹回放：仅 ACCEPTED 点；漂移丢弃点不入轨迹，重复补传点本就不入库。
     * 解除冻结后历史轨迹仍可按档案查询。 */
    @Transactional(readOnly = true)
    public List<TrackView> tracks(Long id, LoginUser user) {
        CorrectionObject o = accessControl.loadVisible(id, user);
        return trackPointRepository
                .findByOffender_IdAndResultOrderByPointTimeAscIdAsc(id, TrackPoint.IngestResult.ACCEPTED)
                .stream()
                .map(t -> new TrackView(t.getClientPointId(), t.getPointTime(), t.getLat(), t.getLng(),
                        t.getOfflineCaptured(), t.getReceivedAt(), t.getOutsideFence(),
                        Boolean.TRUE.equals(t.getForbiddenZone()), t.getResult().name(),
                        t.getBattery(), t.getSignal(), t.getWorn()))
                .toList();
    }

    static String labelOf(String statusName) {
        try {
            return CorrectionStatus.valueOf(statusName).getLabel();
        } catch (Exception e) {
            return statusName;
        }
    }

    static DashboardView.RedDotItem toRedDot(ViolationEvent v, CorrectionObject o) {
        return new DashboardView.RedDotItem(
                v.getId(), o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getName(), o.getOffice().getTimezone(),
                v.getType(), typeLabel(v.getType()),
                v.getDetail(), v.getEventTime(), v.getReadFlag(),
                v.getCaseId(), null, null);
    }

    static DashboardView.RedDotItem toRedDotWithCase(ViolationEvent v, CorrectionObject o,
                                                     String caseNo, String caseStatus) {
        return new DashboardView.RedDotItem(
                v.getId(), o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                o.getOffice().getName(), o.getOffice().getTimezone(),
                v.getType(), typeLabel(v.getType()),
                v.getDetail(), v.getEventTime(), v.getReadFlag(),
                v.getCaseId(), caseNo, caseStatus);
    }

    static String typeLabel(String type) {
        return ViolationCaseService.reasonLabel(type);
    }
}
