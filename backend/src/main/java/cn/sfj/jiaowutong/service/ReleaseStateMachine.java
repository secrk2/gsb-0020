package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.ReleaseStage;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 解除矫正状态机。
 * <pre>
 *   DRAFT ──→ SUBMITTED ──→ APPROVED ──→ DECLARED（终态，打永久标记）
 *                │  ↑── REJECTED ←──┘
 *                └────────→ REJECTED（退回补正后可 RESUBMIT 回 SUBMITTED）
 * </pre>
 * DECLARED 为终态；不得跳过评估直接解除，退回后允许补正重新提交。
 */
public final class ReleaseStateMachine {

    private static final Map<ReleaseStage, Set<ReleaseStage>> ALLOWED = new EnumMap<>(ReleaseStage.class);

    static {
        ALLOWED.put(ReleaseStage.DRAFT, EnumSet.of(ReleaseStage.SUBMITTED));
        ALLOWED.put(ReleaseStage.SUBMITTED, EnumSet.of(ReleaseStage.APPROVED, ReleaseStage.REJECTED));
        ALLOWED.put(ReleaseStage.APPROVED, EnumSet.of(ReleaseStage.DECLARED));
        ALLOWED.put(ReleaseStage.REJECTED, EnumSet.of(ReleaseStage.SUBMITTED));
        ALLOWED.put(ReleaseStage.DECLARED, EnumSet.noneOf(ReleaseStage.class));
    }

    private ReleaseStateMachine() {
    }

    public static void assertTransition(ReleaseStage from, ReleaseStage to) {
        if (from == to) {
            throw new ApiException("RELEASE_TRANSITION",
                    "阶段未发生变化，当前已是「" + from.getLabel() + "」");
        }
        if (from == ReleaseStage.DECLARED) {
            throw new ApiException("RELEASE_TRANSITION",
                    "非法回退：矫正已宣告解除并打上永久标记，解除流程终结，不允许再变更评估或重新在矫");
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            String hint = switch (from) {
                case DRAFT -> "评估报告尚未提交，须先提交审批";
                case SUBMITTED -> "待审批阶段只能审批通过或退回补正";
                case APPROVED -> "审批通过后须宣告解除才能打永久标记，不能退回草稿";
                case REJECTED -> "评估被退回，须补正后重新提交";
                default -> "当前阶段不允许该变更";
            };
            throw new ApiException("RELEASE_TRANSITION",
                    "非法解除流转：「" + from.getLabel() + "」→「" + to.getLabel() + "」。" + hint);
        }
    }
}
