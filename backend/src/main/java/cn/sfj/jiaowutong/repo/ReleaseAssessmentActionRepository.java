package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ReleaseAssessmentAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReleaseAssessmentActionRepository extends JpaRepository<ReleaseAssessmentAction, Long> {

    List<ReleaseAssessmentAction> findByAssessmentIdOrderByCreatedAtAscIdAsc(Long assessmentId);
}
