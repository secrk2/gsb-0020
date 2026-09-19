package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.ReleaseAssessmentStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 解除评估状态机：
 * <pre>
 * 待完善 DRAFT ──提交──→ 待审批 SUBMITTED ──审批通过──→ 已批准 APPROVED ──执行解除──→ 已解除归档 DONE
 *                            └──审批驳回──→ 已驳回 REJECTED ──重新提交──→ 待审批 SUBMITTED
 * </pre>
 * 未提交不能审批；未批准不能执行解除；已解除归档为终态。
 */
public final class ReleaseAssessmentStateMachine {

    private static final Map<ReleaseAssessmentStatus, Set<ReleaseAssessmentStatus>> ALLOWED =
            new EnumMap<>(ReleaseAssessmentStatus.class);

    static {
        ALLOWED.put(ReleaseAssessmentStatus.DRAFT, EnumSet.of(ReleaseAssessmentStatus.SUBMITTED));
        ALLOWED.put(ReleaseAssessmentStatus.SUBMITTED, EnumSet.of(
                ReleaseAssessmentStatus.APPROVED, ReleaseAssessmentStatus.REJECTED));
        ALLOWED.put(ReleaseAssessmentStatus.REJECTED, EnumSet.of(ReleaseAssessmentStatus.SUBMITTED));
        ALLOWED.put(ReleaseAssessmentStatus.APPROVED, EnumSet.of(ReleaseAssessmentStatus.DONE));
        ALLOWED.put(ReleaseAssessmentStatus.DONE, EnumSet.noneOf(ReleaseAssessmentStatus.class));
    }

    private ReleaseAssessmentStateMachine() {
    }

    public static void assertTransition(ReleaseAssessmentStatus from, ReleaseAssessmentStatus to) {
        if (from == ReleaseAssessmentStatus.DONE) {
            throw new ApiException("INVALID_ACTION",
                    "解除已执行完毕并永久归档，评估流程为终态，不允许再变更");
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException("INVALID_ACTION",
                    "解除评估当前为「" + from.getLabel() + "」，不允许直接变更为「" + to.getLabel()
                            + "」。合法下一步：" + allowedText(from));
        }
    }

    private static String allowedText(ReleaseAssessmentStatus from) {
        return ALLOWED.getOrDefault(from, Set.of()).stream()
                .map(ReleaseAssessmentStatus::getLabel)
                .reduce((a, b) -> a + "、" + b)
                .orElse("无");
    }
}
