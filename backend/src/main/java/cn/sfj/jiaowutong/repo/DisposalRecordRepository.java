package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.DisposalCategory;
import cn.sfj.jiaowutong.domain.DisposalRecord;
import cn.sfj.jiaowutong.domain.DisposalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DisposalRecordRepository extends JpaRepository<DisposalRecord, Long> {

    List<DisposalRecord> findByOfficeIdInAndStatusOrderByRegisteredAtDescIdDesc(
            List<Long> officeIds, DisposalStatus status);

    List<DisposalRecord> findByOfficeIdInOrderByRegisteredAtDescIdDesc(List<Long> officeIds);

    List<DisposalRecord> findByOffender_IdOrderByRegisteredAtDescIdDesc(Long offenderId);

    /**
     * 时间窗去重：查同一对象同一事由、状态仍为“待处置”、登记时间不早于窗口起点的最近一条。
     * 命中即把新事件并入该单，不再新开单。
     */
    Optional<DisposalRecord> findFirstByOffender_IdAndCategoryAndStatusAndRegisteredAtAfterOrderByRegisteredAtDescIdDesc(
            Long offenderId, DisposalCategory category, DisposalStatus status, Instant windowStart);

    long countByOfficeIdInAndStatus(List<Long> officeIds, DisposalStatus status);

    /** 按业务编号前缀计数，用于生成“当日第几单”的顺序号。 */
    long countByDisposalNoStartingWith(String prefix);
}
