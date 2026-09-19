package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 解除评估报告视图。报告客观数据在生成时固化；解除证明书编号为永久标记。
 */
public record ReleaseAssessmentView(Long id, String reportNo,
                                    Long objectId, String correctionNo, String maskedName,
                                    Long officeId, String officeName, String timezone,
                                    String status, String statusLabel, LocalDate dueDate,
                                    Double checkinDayRate, Double keyReportRate,
                                    Integer trackActiveDays, Integer breachCount30d,
                                    Integer admonishCount, Boolean openViolationCase,
                                    String conclusion, String conclusionLabel, String opinion,
                                    String generatedByName, Instant generatedAt,
                                    Instant submittedAt, String approvedByName, Instant approvedAt,
                                    Instant rejectedAt,
                                    Instant releasedAt, String releaseCertificateNo, String releasedByName,
                                    List<String> allowedActions) {

    public record ActionView(Long id, String action, String actionLabel,
                             String operatorName, String reason,
                             String statusAfter, String statusAfterLabel,
                             String detail, Instant createdAt) {
    }

    public record DetailView(ReleaseAssessmentView assessment, List<ActionView> actions,
                             String objectStatus, String objectStatusLabel,
                             String charge, LocalDate startDate, LocalDate endDate) {
    }

    /** 到期/临期对象条目 */
    public record DueItem(Long objectId, String correctionNo, String maskedName,
                          String officeName, String timezone,
                          String status, String statusLabel,
                          LocalDate endDate, long daysToDue, boolean overdue,
                          Long assessmentId, String reportNo, String assessmentStatus,
                          String assessmentStatusLabel) {
    }
}
