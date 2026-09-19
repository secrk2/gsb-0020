package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.util.List;

/**
 * 违规处置案件视图。mergedEventCount = 时间窗内合并到同一案件的原始预警条数，
 * 用于界面说明“同晚同类预警只立一条案件”。
 */
public record ViolationCaseView(Long id, String caseNo,
                                Long objectId, String correctionNo, String maskedName,
                                Long officeId, String officeName, String timezone,
                                String reasonType, String reasonTypeLabel, String summary,
                                String status, String statusLabel,
                                Instant registeredAt, String registeredByName, Instant closedAt,
                                int mergedEventCount) {

    public record ActionView(Long id, String action, String actionLabel,
                             String operatorName, String reason,
                             String statusAfter, String statusAfterLabel,
                             String detail, Instant createdAt) {
    }

    public record DetailView(ViolationCaseView caseInfo,
                             List<ActionView> actions,
                             List<DashboardView.RedDotItem> events,
                             String objectStatus, String objectStatusLabel,
                             List<String> allowedActions) {
    }
}
