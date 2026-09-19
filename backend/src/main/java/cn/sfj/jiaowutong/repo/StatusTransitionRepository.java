package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.StatusTransition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StatusTransitionRepository extends JpaRepository<StatusTransition, Long> {

    List<StatusTransition> findByOffenderIdOrderByOperatedAtDescIdDesc(Long offenderId);

    List<StatusTransition> findByOffenderIdAndToStatusOrderByOperatedAtDescIdDesc(
            Long offenderId, String toStatus);

    /** 矫正期内某目标状态的流转次数（解除评估统计训诫次数用，toStatus 为枚举名串） */
    long countByOffenderIdAndToStatus(Long offenderId, String toStatus);
}
