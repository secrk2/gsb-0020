package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import cn.sfj.jiaowutong.repo.CheckInRepository;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumSet;

/**
 * 未按日报到检测（定时）。
 * 每个对象按其<b>所属司法所时区</b>判断“今天星期几/是否过 18:00”：规定报到日当天逾时仍无报到记录，
 * 生成一条 ABSENT 红点。事件统一经 {@link ViolationEventService} 发出——
 * 同对象同类型未挂单红点边沿去重、12 小时窗内已有待处置单则并入，不会一晚刷出多条。
 */
@Service
public class AbsentDetectionService {

    private static final Logger log = LoggerFactory.getLogger(AbsentDetectionService.class);
    private static final LocalTime OVERDUE_AFTER = LocalTime.of(18, 0);
    private static final EnumSet<CorrectionStatus> ACTIVE =
            EnumSet.of(CorrectionStatus.SERVING, CorrectionStatus.LEAVE, CorrectionStatus.ADMONISHED);

    private final CorrectionObjectRepository objectRepository;
    private final CheckInRepository checkInRepository;
    private final ViolationEventService violationEventService;

    public AbsentDetectionService(CorrectionObjectRepository objectRepository,
                                  CheckInRepository checkInRepository,
                                  ViolationEventService violationEventService) {
        this.objectRepository = objectRepository;
        this.checkInRepository = checkInRepository;
        this.violationEventService = violationEventService;
    }

    /** 每 10 分钟扫描一次；各对象按本所时区独立判日/判时。 */
    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void detectAbsent() {
        Instant now = Instant.now();
        int created = 0;
        for (CorrectionObject o : objectRepository.findAll()) {
            if (!ACTIVE.contains(o.getStatus())) {
                continue;
            }
            ZoneId zone = FenceService.safeZone(o.getOffice().getTimezone());
            ZonedDateTime localNow = now.atZone(zone);
            LocalDate localToday = localNow.toLocalDate();
            if (o.getReportDay() == null
                    || !localNow.getDayOfWeek().toString().equals(o.getReportDay())) {
                continue;
            }
            if (checkInRepository.existsByOffender_IdAndCheckDate(o.getId(), localToday)) {
                continue;
            }
            Instant overdueAt = localToday.atTime(OVERDUE_AFTER).atZone(zone).toInstant();
            if (now.isBefore(overdueAt)) {
                continue;
            }
            boolean emitted = violationEventService.emit(o, "ABSENT",
                    "对象 " + o.getMaskedName() + " 规定每"
                            + Weekday.label(o.getReportDay())
                            + "报到，" + localToday + "（" + o.getOffice().getTimezone()
                            + "）截至 18:00 未报到", now);
            if (emitted) {
                created++;
            }
        }
        if (created > 0) {
            log.info("未报到检测：新生成 ABSENT 红点 {} 条", created);
        }
    }
}
