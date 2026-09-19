package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    long countByOfficeIdAndReadFlagFalse(Long officeId);

    List<ViolationEvent> findTop10ByOfficeIdAndReadFlagFalseOrderByEventTimeDesc(Long officeId);

    List<ViolationEvent> findTop20ByOffender_IdOrderByEventTimeDesc(Long offenderId);

    List<ViolationEvent> findByCaseIdOrderByEventTimeDescIdDesc(Long caseId);

    /** 某对象某类尚未登记到案件、发生在指定时刻之后的原始预警（登记时时间窗合并） */
    List<ViolationEvent> findByOffender_IdAndTypeAndCaseIdIsNullAndEventTimeAfterOrderByEventTimeDescIdDesc(
            Long offenderId, String type, Instant after);

    /** 某对象指定类型、发生在指定时刻之后的全部预警（解除评估 30 天统计用） */
    long countByOffender_IdAndTypeInAndEventTimeAfter(
            Long offenderId, List<String> types, Instant after);

    /** 某对象全部未处置红点（执行解除时统一核销用） */
    List<ViolationEvent> findByOffender_IdAndReadFlagFalse(Long offenderId);

    /** 某对象指定类型晚于某时刻的最近事件（未报到每日去重用） */
    ViolationEvent findFirstByOffender_IdAndTypeAndEventTimeAfterOrderByEventTimeDesc(
            Long offenderId, String type, Instant after);

    /** 是否已有某类未处置红点（同类事件边沿去重） */
    boolean existsByOffender_IdAndTypeAndReadFlagFalse(Long offenderId, String type);
}
