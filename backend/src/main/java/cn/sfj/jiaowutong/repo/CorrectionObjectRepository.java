package cn.sfj.jiaowutong.repo;

import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CorrectionObjectRepository extends JpaRepository<CorrectionObject, Long> {

    Optional<CorrectionObject> findByCorrectionNo(String correctionNo);

    /** 编号模糊检索（解除归档后仍可按编号查到档案） */
    List<CorrectionObject> findByCorrectionNoContainingIgnoreCaseOrderByCorrectionNo(String keyword);

    List<CorrectionObject> findByOfficeIdOrderById(Long officeId);

    List<CorrectionObject> findByOfficeIdAndStatusOrderById(Long officeId, CorrectionStatus status);

    long countByOfficeIdAndStatus(Long officeId, CorrectionStatus status);

    long countByStatus(CorrectionStatus status);

    List<CorrectionObject> findByStatusOrderById(CorrectionStatus status);
}
