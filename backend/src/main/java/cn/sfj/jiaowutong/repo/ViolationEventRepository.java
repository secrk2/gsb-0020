package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ViolationEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViolationEventRepository extends JpaRepository<ViolationEvent, Long> {

    long countByOfficeIdAndReadFlagFalse(Long officeId);

    List<ViolationEvent> findTop10ByOfficeIdAndReadFlagFalseOrderByEventTimeDesc(Long officeId);

    List<ViolationEvent> findTop20ByOffender_IdOrderByEventTimeDesc(Long offenderId);

    /** 尚未挂到任何处置单、用于登记时合并的事件（按对象）。 */
    List<ViolationEvent> findByOffender_IdAndDisposalIsNullOrderByEventTimeDescIdDesc(Long offenderId);

    /** 处置单撤销后，把原先挂在该单上的红点取回来重新挂空。 */
    List<ViolationEvent> findByDisposal_Id(Long disposalId);

    long countByDisposal_Id(Long disposalId);

    /** 处置中心“待登记受理”的红点：未挂任何处置单、未核销。 */
    List<ViolationEvent> findByDisposalIsNullAndReadFlagFalseOrderByEventTimeDescIdDesc();
}
