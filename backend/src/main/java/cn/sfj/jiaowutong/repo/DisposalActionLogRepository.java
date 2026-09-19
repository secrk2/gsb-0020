package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.DisposalActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DisposalActionLogRepository extends JpaRepository<DisposalActionLog, Long> {

    List<DisposalActionLog> findByDisposalIdOrderByOperatedAtAscIdAsc(Long disposalId);
}
