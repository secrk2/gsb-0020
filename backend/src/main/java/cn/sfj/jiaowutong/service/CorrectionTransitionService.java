package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.CorrectionObject;
import cn.sfj.jiaowutong.domain.CorrectionStatus;
import cn.sfj.jiaowutong.domain.StatusTransition;
import cn.sfj.jiaowutong.repo.CorrectionObjectRepository;
import cn.sfj.jiaowutong.repo.StatusTransitionRepository;
import cn.sfj.jiaowutong.security.LoginUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 矫正档案状态流转的统一入口：所有需要改变矫正状态的业务（手工流转、违规训诫/收监、
 * 解除执行）都走这里，由 {@link CorrectionStateMachine} 裁决合法性并写 status_transition 留痕，
 * 不允许任何业务直接 setStatus 改结论。
 */
@Service
public class CorrectionTransitionService {

    private final CorrectionObjectRepository objectRepository;
    private final StatusTransitionRepository transitionRepository;

    public CorrectionTransitionService(CorrectionObjectRepository objectRepository,
                                       StatusTransitionRepository transitionRepository) {
        this.objectRepository = objectRepository;
        this.transitionRepository = transitionRepository;
    }

    @Transactional
    public StatusTransition apply(CorrectionObject obj, CorrectionStatus target,
                                  String reason, LoginUser user) {
        CorrectionStatus from = obj.getStatus();
        CorrectionStateMachine.assertTransition(from, target);
        obj.setStatus(target);
        objectRepository.save(obj);
        StatusTransition transition = new StatusTransition(
                obj.getId(), from, target, user.userId(), user.realName(), reason);
        return transitionRepository.save(transition);
    }

    /** 状态已终态或不存在时给出 409，供各业务在前置校验中统一引用 */
    public static ApiException terminalConflict(CorrectionStatus status) {
        return new ApiException("INVALID_ACTION",
                "对象当前为「" + status.getLabel() + "」，矫正流程已终结，不能再发起该处置");
    }
}
