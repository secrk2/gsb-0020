package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.DisposalStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 违规处置单状态机：
 * <pre>
 *   已登记 REGISTERED ─┬─→ 训诫 ADMONISHED（已执行，处置终结）
 *                     ├─→ 收监 REIMPRISONED（已执行，处置终结；收监不可逆）
 *                     ├─→ 驳回 REJECTED（经查不构成违规，终结）
 *                     └─→ 撤销 REVOKED（登记有误，终结）
 * </pre>
 * 训诫/收监属已执行的处置结论，处置单上不可撤销或改写；训诫后如需让对象恢复在矫，
 * 走档案状态机的「教育改正·恢复在矫」。驳回与撤销仅在“已登记·待处置”阶段可用，
 * 保证“结论不许直接改”，每一步只能沿状态机推进并落留痕。
 */
public final class DisposalStateMachine {

    private static final Map<DisposalStatus, Set<DisposalStatus>> ALLOWED = new EnumMap<>(DisposalStatus.class);

    static {
        ALLOWED.put(DisposalStatus.REGISTERED, EnumSet.of(
                DisposalStatus.ADMONISHED, DisposalStatus.REIMPRISONED,
                DisposalStatus.REJECTED, DisposalStatus.REVOKED));
        ALLOWED.put(DisposalStatus.ADMONISHED, EnumSet.noneOf(DisposalStatus.class));
        ALLOWED.put(DisposalStatus.REIMPRISONED, EnumSet.noneOf(DisposalStatus.class));
        ALLOWED.put(DisposalStatus.REJECTED, EnumSet.noneOf(DisposalStatus.class));
        ALLOWED.put(DisposalStatus.REVOKED, EnumSet.noneOf(DisposalStatus.class));
    }

    private DisposalStateMachine() {
    }

    /** 非法推进抛 DISPOSAL_TRANSITION（HTTP 409），消息说明原因与当前可做的处置。 */
    public static void assertTransition(DisposalStatus from, DisposalStatus to) {
        if (from == to) {
            throw new ApiException("DISPOSAL_TRANSITION",
                    "处置结论未发生变化，该处置单当前已是「" + from.getLabel() + "」");
        }
        if (from != DisposalStatus.REGISTERED) {
            throw new ApiException("DISPOSAL_TRANSITION",
                    "处置结论不可改写：该处置单已办结为「" + from.getLabel()
                            + "」。训诫/收监一经执行即终结，不能在处置单上撤销或改判；"
                            + "如需让训诫对象恢复在矫，请在档案上走「教育改正·恢复在矫」");
        }
        if (!ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            throw new ApiException("DISPOSAL_TRANSITION",
                    "当前处置阶段「" + from.getLabel() + "」不允许推进到「" + to.getLabel() + "」");
        }
    }
}
