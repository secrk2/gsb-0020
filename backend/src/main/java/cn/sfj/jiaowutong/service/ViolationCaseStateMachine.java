package cn.sfj.jiaowutong.service;

import cn.sfj.jiaowutong.common.ApiException;
import cn.sfj.jiaowutong.domain.ViolationCaseStatus;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 违规处置案件状态机：
 * <pre>
 *        登记受理 REGISTERED
 *          ├── 予以训诫  → ADMONISHED（同时驱动矫正状态机：在矫/请假 → 训诫）
 *          ├── 提请收监  → REIMPRISONED（同时驱动矫正状态机 → 收监，终态）
 *          ├── 驳回      → REJECTED（预警失实/证据不足，留理由）
 *          └── 撤销      → REVOKED（重复登记/对象错误，留理由）
 * </pre>
 * 终态（训诫/收监/驳回/撤销）不可再操作；结论只能通过追加动作推进，禁止直接改字段。
 */
public final class ViolationCaseStateMachine {

    private static final Map<ViolationCaseStatus, Set<ViolationCaseStatus>> ALLOWED =
            new EnumMap<>(ViolationCaseStatus.class);

    static {
        ALLOWED.put(ViolationCaseStatus.REGISTERED, EnumSet.of(
                ViolationCaseStatus.ADMONISHED, ViolationCaseStatus.REIMPRISONED,
                ViolationCaseStatus.REJECTED, ViolationCaseStatus.REVOKED));
        ALLOWED.put(ViolationCaseStatus.ADMONISHED, EnumSet.noneOf(ViolationCaseStatus.class));
        ALLOWED.put(ViolationCaseStatus.REIMPRISONED, EnumSet.noneOf(ViolationCaseStatus.class));
        ALLOWED.put(ViolationCaseStatus.REJECTED, EnumSet.noneOf(ViolationCaseStatus.class));
        ALLOWED.put(ViolationCaseStatus.REVOKED, EnumSet.noneOf(ViolationCaseStatus.class));
    }

    private ViolationCaseStateMachine() {
    }

    public static void assertAction(ViolationCaseStatus current, ViolationCaseStatus target) {
        if (current != ViolationCaseStatus.REGISTERED) {
            throw new ApiException("INVALID_ACTION",
                    "案件当前为「" + current.getLabel() + "」终态，处置结论已经作出并留痕，"
                            + "不允许再次处置或直接修改结论；如结论有误须按程序另行登记新案件");
        }
        if (!ALLOWED.get(current).contains(target)) {
            throw new ApiException("INVALID_ACTION",
                    "当前案件状态不允许该处置动作");
        }
    }
}
