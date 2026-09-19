package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ReleaseAssessment;
import cn.sfj.jiaowutong.domain.ReleaseAssessmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReleaseAssessmentRepository extends JpaRepository<ReleaseAssessment, Long> {

    List<ReleaseAssessment> findAllByOrderByGeneratedAtDescIdDesc();

    List<ReleaseAssessment> findByStatusOrderByGeneratedAtDescIdDesc(ReleaseAssessmentStatus status);

    List<ReleaseAssessment> findByOfficeIdAndStatusOrderByGeneratedAtDescIdDesc(Long officeId,
                                                                               ReleaseAssessmentStatus status);

    /** 一个对象只保留一份在途评估（非已解除归档），防止重复发起 */
    Optional<ReleaseAssessment> findFirstByOffender_IdAndStatusNotOrderByIdDesc(
            Long offenderId, ReleaseAssessmentStatus status);

    Optional<ReleaseAssessment> findByOffender_IdAndStatus(Long offenderId, ReleaseAssessmentStatus status);

    List<ReleaseAssessment> findByOffender_IdOrderByGeneratedAtDescIdDesc(Long offenderId);
}
