package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ViolationCase;
import cn.sfj.jiaowutong.domain.ViolationCaseStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface ViolationCaseRepository extends JpaRepository<ViolationCase, Long> {

    List<ViolationCase> findAllByOrderByRegisteredAtDescIdDesc();

    List<ViolationCase> findByStatusOrderByRegisteredAtDescIdDesc(ViolationCaseStatus status);

    List<ViolationCase> findByOfficeIdOrderByRegisteredAtDescIdDesc(Long officeId);

    List<ViolationCase> findByOfficeIdAndStatusOrderByRegisteredAtDescIdDesc(Long officeId,
                                                                             ViolationCaseStatus status);

    /** 同一对象同一事由最近的待处置案件（时间窗合并去重用） */
    ViolationCase findTopByOffender_IdAndReasonTypeAndStatusOrderByRegisteredAtDesc(
            Long offenderId, String reasonType, ViolationCaseStatus status);

    boolean existsByOffender_IdAndStatus(Long offenderId, ViolationCaseStatus status);

    List<ViolationCase> findByOffender_IdOrderByRegisteredAtDescIdDesc(Long offenderId);

    List<ViolationCase> findByOffender_IdAndStatusInOrderByRegisteredAtDescIdDesc(
            Long offenderId, List<ViolationCaseStatus> statuses);

    long countByRegisteredAtAfter(Instant after);
}
