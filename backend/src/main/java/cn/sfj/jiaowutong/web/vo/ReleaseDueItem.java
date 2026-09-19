package cn.sfj.jiaowutong.web.vo;

import java.time.LocalDate;

/**
 * 解除到期清单条目：矫正期将满/已满、且尚未宣告解除的在管对象。
 * daysUntilExpiry：距期满天数（负数表示已逾期未办理解除的天数）。
 */
public record ReleaseDueItem(Long objectId, String correctionNo, String maskedName,
                             Long officeId, String officeName, String timezone,
                             String status, String statusLabel,
                             LocalDate endDate, long daysUntilExpiry, boolean overdue,
                             boolean hasAssessment, String assessmentStage, String assessmentStageLabel,
                             String assessmentNo) {
}
