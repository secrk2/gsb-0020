package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.ViolationCaseAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ViolationCaseActionRepository extends JpaRepository<ViolationCaseAction, Long> {

    List<ViolationCaseAction> findByCaseIdOrderByCreatedAtAscIdAsc(Long caseId);
}
