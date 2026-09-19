package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ReleaseActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReleaseActionLogRepository extends JpaRepository<ReleaseActionLog, Long> {

    List<ReleaseActionLog> findByAssessmentIdOrderByOperatedAtAscIdAsc(Long assessmentId);
}
