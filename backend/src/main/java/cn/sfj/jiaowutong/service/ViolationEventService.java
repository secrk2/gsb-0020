package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.domain.*;
import cn.sfj.jiaowutong.repo.DisposalRecordRepository;
import cn.sfj.jiaowutong.repo.ViolationEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * 违规/越界红点事件的统一入口，所有事件生成都走这里，保证两条规则：
 * 1. <b>红点边沿</b>：同一对象同一类型只要还挂着未处置（未登记入单）红点，就不再产生新红点，
 *    连续 5 秒越界不会刷一屏；
 * 2. <b>时间窗内只合成一条处置单</b>：若该对象该事由已有「已登记·待处置」处置单且仍在
 *    {@link #DISPOSAL_MERGE_WINDOW_HOURS} 时间窗内，本次事件不再新开红点/新单，直接视为同一情节
 *    （越界/禁区等连续情节）或被窗口吞掉（当晚安顿一次即可），杜绝一晚刷出七八条。
 */
@Service
public class ViolationEventService {

    /** 同一对象同一事由合并到同一条待处置单的时间窗（小时） */
    public static final long DISPOSAL_MERGE_WINDOW_HOURS = 12;

    private final ViolationEventRepository violationRepository;
    private final DisposalRecordRepository disposalRepository;

    public ViolationEventService(ViolationEventRepository violationRepository,
                                 DisposalRecordRepository disposalRepository) {
        this.violationRepository = violationRepository;
        this.disposalRepository = disposalRepository;
    }

    /**
     * 产生一条违规红点事件。
     *
     * @return true 若产生了新的、会出现在作战台上的未处置红点；false 表示被边沿去重或被时间窗合并。
     */
    @Transactional
    public boolean emit(CorrectionObject offender, String type, String detail, Instant eventTime) {
        Instant now = Instant.now();
        DisposalCategory category = DisposalCategory.fromEventType(type);

        // 规则一：仍有同类型“未登记入单”的未处置红点 → 边沿去重，不重复报警
        if (hasOpenRedDot(offender.getId(), type)) {
            return false;
        }

        // 规则二：时间窗内已有同一对象同一事由的待处置单 → 同一情节，不另开红点/新单
        boolean openCaseInWindow = disposalRepository
                .findFirstByOffender_IdAndCategoryAndStatusAndRegisteredAtAfterOrderByRegisteredAtDescIdDesc(
                        offender.getId(), category, DisposalStatus.REGISTERED,
                        now.minusSeconds(DISPOSAL_MERGE_WINDOW_HOURS * 3600))
                .isPresent();
        if (openCaseInWindow) {
            return false;
        }

        violationRepository.save(new ViolationEvent(offender, type, detail, eventTime));
        return true;
    }

    /** 是否存在未挂处置单、未核销的同类型红点（连续越界只报一次的边沿判定）。 */
    public boolean hasOpenRedDot(Long offenderId, String type) {
        return violationRepository
                .findByOffender_IdAndDisposalIsNullOrderByEventTimeDescIdDesc(offenderId).stream()
                .filter(v -> type.equals(v.getType()) && !Boolean.TRUE.equals(v.getReadFlag()))
                .findFirst()
                .isPresent();
    }

    /** 核销某处置单下挂的全部红点（训诫/收监/驳回办结时调用）。 */
    @Transactional
    public int resolveEventsOfDisposal(Long disposalId) {
        List<ViolationEvent> evs = violationRepository.findByDisposal_Id(disposalId);
        evs.forEach(e -> e.setReadFlag(true));
        violationRepository.saveAll(evs);
        return evs.size();
    }

    /** 撤销处置单：把原挂的红点全部摘回，重新进入待处置红点列表。 */
    @Transactional
    public int detachEventsOfDisposal(Long disposalId) {
        List<ViolationEvent> evs = violationRepository.findByDisposal_Id(disposalId);
        evs.forEach(e -> {
            e.setDisposal(null);
            e.setReadFlag(false);
        });
        violationRepository.saveAll(evs);
        return evs.size();
    }

    /**
     * 解除/收监等终态：关闭对象所有仍在作战台的未处置红点（未挂单、未核销）。
     * 终态对象不再出现在作战台红点中；历史红点仍在档案可查。
     */
    @Transactional
    public int closeOpenEventsForOffender(Long offenderId) {
        List<ViolationEvent> open = violationRepository
                .findByOffender_IdAndDisposalIsNullOrderByEventTimeDescIdDesc(offenderId).stream()
                .filter(v -> !Boolean.TRUE.equals(v.getReadFlag()))
                .toList();
        open.forEach(e -> e.setReadFlag(true));
        violationRepository.saveAll(open);
        return open.size();
    }
}
