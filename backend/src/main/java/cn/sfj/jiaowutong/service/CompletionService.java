package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.CheckIn;
import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.repo.CheckInRepository;
import cn.sfj.jiaowutong.web.vo.CompletionView;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * 在矫完成度双口径计算（解除评估与定位监控共用同一口径）：
 * - 口径一·打卡天数 = 近 30 天实际有报到天数 / 自然日；
 * - 口径二·关键报到 = 规定报到日当天完成节点数 / 应到节点数。
 * 统计窗口按对象所属司法所时区的日历日。
 */
@Service
public class CompletionService {

    /** 两口径相差超过该百分点视为“结论相反”，界面需显著提示 */
    private static final double OPPOSITE_GAP = 0.25d;

    private final CheckInRepository checkInRepository;

    public CompletionService(CheckInRepository checkInRepository) {
        this.checkInRepository = checkInRepository;
    }

    public CompletionView build(CorrectionObject o, ZoneId zone) {
        LocalDate today = Instant.now().atZone(zone).toLocalDate();
        LocalDate basisFrom = today.minusDays(29);
        LocalDate basisTo = today;

        Set<LocalDate> checkDays = checkInRepository.findByOffender_IdOrderByCheckDateAscIdAsc(o.getId())
                .stream()
                .map(CheckIn::getCheckDate)
                .filter(d -> !d.isBefore(basisFrom) && !d.isAfter(basisTo))
                .collect(Collectors.toCollection(TreeSet::new));

        int calendarDays = (int) (java.time.temporal.ChronoUnit.DAYS.between(basisFrom, basisTo) + 1);
        int actualDays = checkDays.size();
        double dayRate = round1((double) actualDays / calendarDays);

        DayOfWeek reportDay = parseDay(o.getReportDay());
        int dueNodes = 0;
        int doneNodes = 0;
        for (LocalDate d = basisFrom; !d.isAfter(basisTo); d = d.plusDays(1)) {
            if (reportDay != null && d.getDayOfWeek() == reportDay) {
                dueNodes++;
                if (checkDays.contains(d)) {
                    doneNodes++;
                }
            }
        }
        double keyRate = dueNodes == 0 ? 0d : round1((double) doneNodes / dueNodes);
        boolean opposite = Math.abs(dayRate - keyRate) >= OPPOSITE_GAP;

        return new CompletionView(
                basisFrom.toString(), basisTo.toString(), zone.toString(),
                actualDays, calendarDays, dayRate,
                doneNodes, dueNodes, keyRate, opposite,
                CompletionView.CHECKIN_DAY_DEFINITION, CompletionView.KEY_REPORT_DEFINITION);
    }

    private DayOfWeek parseDay(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return DayOfWeek.valueOf(name.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private double round1(double v) {
        return Math.round(v * 100d) / 100d;
    }
}
