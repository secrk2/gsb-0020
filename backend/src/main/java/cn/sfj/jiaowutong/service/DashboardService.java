package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.*;
import cn.sfj.jiaowutong.security.LoginUser;
import cn.sfj.jiaowutong.web.vo.DashboardView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * 矫务作战台聚合：
 * - 各司法所在矫漏斗（入矫登记 / 在矫 / 请假外出 / 训诫 / 收监 / 解除）；
 * - 今日应报到：按对象所属司法所时区取“今天/星期”，并按该时区 18:00 判逾时——
 *   绝不用服务器或干警时区，跨时区对象否则会记错日子、白触发红点；
 * - 逾时未报到自动落 ABSENT 预警（按对象时区当日去重，一天最多一条），
 *   若该对象同类待处置案件在时间窗内则直接并入，不产生新红点；
 * - 红点：未处置且未挂案件的越界/禁区/未报到/训诫事件，按数据范围过滤；
 *   已解除/收监对象不再产生红点。事件时间为 UTC。
 */
@Service
public class DashboardService {

    /** 晚于对象所在时区的该时刻仍未报到视为逾时（红点） */
    private static final LocalTime OVERDUE_AFTER = LocalTime.of(18, 0);

    private final JudicialOfficeRepository officeRepository;
    private final CorrectionObjectRepository objectRepository;
    private final CheckInRepository checkInRepository;
    private final ViolationEventRepository violationRepository;
    private final ViolationCaseRepository caseRepository;
    private final ViolationCaseService caseService;

    public DashboardService(JudicialOfficeRepository officeRepository,
                            CorrectionObjectRepository objectRepository,
                            CheckInRepository checkInRepository,
                            ViolationEventRepository violationRepository,
                            ViolationCaseRepository caseRepository,
                            ViolationCaseService caseService) {
        this.officeRepository = officeRepository;
        this.objectRepository = objectRepository;
        this.checkInRepository = checkInRepository;
        this.violationRepository = violationRepository;
        this.caseRepository = caseRepository;
        this.caseService = caseService;
    }

    @Transactional
    public DashboardView build(LoginUser user) {
        Instant nowUtc = Instant.now();

        List<JudicialOffice> offices = officeRepository.findAll();
        List<CorrectionObject> all = objectRepository.findAll();

        // 数据范围：监管员全区；干警本所；对象本人（作战台对对象仅给本人摘要）
        List<CorrectionObject> scoped;
        if (user.role() == Role.SUPERVISOR) {
            scoped = all;
        } else if (user.role() == Role.STAFF) {
            scoped = all.stream().filter(o -> user.officeId().equals(o.getOffice().getId())).toList();
        } else {
            scoped = all.stream()
                    .filter(o -> user.offenderId() != null && user.offenderId().equals(o.getId()))
                    .toList();
        }

        Map<CorrectionStatus, Long> globalCounts = new EnumMap<>(CorrectionStatus.class);
        for (CorrectionStatus s : CorrectionStatus.values()) {
            globalCounts.put(s, 0L);
        }
        for (CorrectionObject o : scoped) {
            globalCounts.merge(o.getStatus(), 1L, Long::sum);
        }
        Map<String, Long> globalFunnel = new LinkedHashMap<>();
        for (CorrectionStatus s : List.of(CorrectionStatus.INTAKE, CorrectionStatus.SERVING,
                CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED,
                CorrectionStatus.REIMPRISONED, CorrectionStatus.RELEASED)) {
            globalFunnel.put(s.name(), globalCounts.get(s));
        }

        List<DashboardView.OfficeFunnel> officeFunnels = new ArrayList<>();
        for (JudicialOffice office : offices) {
            List<CorrectionObject> inOffice = all.stream()
                    .filter(o -> o.getOffice().getId().equals(office.getId()))
                    .toList();
            // 干警只能看到本所卡片；监管员看全部；对象视角不暴露其他所
            if (user.role() == Role.STAFF && !office.getId().equals(user.officeId())) {
                continue;
            }
            if (user.role() == Role.OFFENDER
                    && inOffice.stream().noneMatch(o -> o.getId().equals(user.offenderId()))) {
                continue;
            }
            long intake = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.INTAKE).count();
            long serving = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.SERVING).count();
            long leave = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.LEAVE).count();
            long admonished = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.ADMONISHED).count();
            long reimprisoned = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.REIMPRISONED).count();
            long released = inOffice.stream().filter(o -> o.getStatus() == CorrectionStatus.RELEASED).count();
            // 在矫口径总量：在矫+请假外出+训诫（监外执行中）
            long activeTotal = serving + leave + admonished;
            officeFunnels.add(new DashboardView.OfficeFunnel(
                    office.getId(), office.getName(), office.getRegion(), office.getTimezone(),
                    intake, serving, leave, admonished, reimprisoned, released, activeTotal));
        }

        // 今日应报到：在矫/请假/训诫状态，按“对象所在司法所时区的今天星期”匹配
        List<DashboardView.DueTodayItem> due = new ArrayList<>();
        for (CorrectionObject o : scoped) {
            if (!EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.LEAVE,
                    CorrectionStatus.ADMONISHED).contains(o.getStatus())) {
                continue;
            }
            ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
            ZonedDateTime localNow = nowUtc.atZone(zone);
            LocalDate localToday = localNow.toLocalDate();
            if (!localNow.getDayOfWeek().toString().equals(o.getReportDay())) {
                continue;
            }
            boolean checked = checkInRepository.existsByOffender_IdAndCheckDate(o.getId(), localToday);
            // 逾时阈值也按对象时区墙钟：该时区今天 18:00 对应的 UTC 时刻
            Instant overdueAt = localToday.atTime(OVERDUE_AFTER).atZone(zone).toInstant();
            boolean overdue = !checked && nowUtc.isAfter(overdueAt);
            if (overdue) {
                materializeAbsentEvent(o, localToday, nowUtc);
            }
            due.add(new DashboardView.DueTodayItem(
                    o.getId(), o.getCorrectionNo(), o.getMaskedName(),
                    o.getOffice().getName(), o.getOffice().getTimezone(), localToday.toString(),
                    o.getReportDay(), checked, overdue));
        }
        due.sort(Comparator.comparing(DashboardView.DueTodayItem::correctionNo));

        // 红点：未处置、未挂案件事件；已解除/收监对象不出红点；按范围过滤
        Map<Long, ViolationCase> caseMap = new HashMap<>();
        List<DashboardView.RedDotItem> redDots = violationRepository.findAll().stream()
                .filter(v -> !v.getReadFlag() && v.getCaseId() == null)
                .filter(v -> {
                    CorrectionStatus st = v.getOffender().getStatus();
                    return st != CorrectionStatus.RELEASED && st != CorrectionStatus.REIMPRISONED;
                })
                .filter(v -> scoped.stream().anyMatch(o -> o.getId().equals(v.getOffender().getId())))
                .sorted(Comparator.comparing(ViolationEvent::getEventTime).reversed())
                .limit(30)
                .map(v -> {
                    ViolationCase c = v.getCaseId() == null ? null
                            : caseMap.computeIfAbsent(v.getCaseId(),
                                    id -> caseRepository.findById(id).orElse(null));
                    return ObjectService.toRedDotWithCase(v, v.getOffender(),
                            c == null ? null : c.getCaseNo(),
                            c == null ? null : c.getStatus().name());
                })
                .toList();

        return new DashboardView(nowUtc.toString(), user.role().name(), globalFunnel,
                officeFunnels, due, redDots, redDots.size());
    }

    /**
     * 逾时未报到落预警：每个对象当地当日最多一条；
     * 同类待处置案件仍在合并时间窗内时，直接并入案件不刷红点。
     */
    private void materializeAbsentEvent(CorrectionObject o, LocalDate localToday, Instant now) {
        ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
        Instant dayStartUtc = localToday.atStartOfDay(zone).toInstant();
        ViolationEvent existed = violationRepository
                .findFirstByOffender_IdAndTypeAndEventTimeAfterOrderByEventTimeDesc(
                        o.getId(), "ABSENT", dayStartUtc);
        if (existed != null) {
            return;
        }
        ViolationEvent event = new ViolationEvent(o, "ABSENT",
                "对象 " + o.getMaskedName() + " 应于今日（" + localToday + " " + o.getOffice().getTimezone()
                        + "）报到，截至当地 18:00 未报到", now);
        ViolationCaseService.EventAttach attach = caseService.attachNewEvent(event);
        if (attach != ViolationCaseService.EventAttach.SUPPRESSED) {
            violationRepository.save(event);
        }
    }
}
