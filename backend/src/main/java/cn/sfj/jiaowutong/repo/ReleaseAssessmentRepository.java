package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ReleaseAssessment;
import cn.sfj.jiaowutong.domain.ReleaseStage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReleaseAssessmentRepository extends JpaRepository<ReleaseAssessment, Long> {

    List<ReleaseAssessment> findByOffender_IdOrderByCreatedAtDescIdDesc(Long offenderId);

    /** 该对象是否已有一份“尚未宣告”的评估（防止重复发起解除流程）。 */
    Optional<ReleaseAssessment> findFirstByOffender_IdAndStageNotOrderByIdDesc(
            Long offenderId, ReleaseStage stage);

    boolean existsByOffender_IdAndStage(Long offenderId, ReleaseStage stage);

    long countByAssessmentNoStartingWith(String prefix);
}
