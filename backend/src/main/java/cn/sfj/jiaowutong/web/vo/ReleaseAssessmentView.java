package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * 解除评估报告详情：报告字段 + 对象摘要 + 近 30 天考核双口径（写进报告供评估参考）+ 逐步留痕。
 */
public record ReleaseAssessmentView(Long id, String assessmentNo,
                                    Long objectId, String correctionNo, String maskedName,
                                    Long officeId, String officeName, String timezone,
                                    String stage, String stageLabel,
                                    LocalDate endDate, long daysUntilExpiry, boolean overdue,
                                    Integer score, String education, String compliance,
                                    String repentance, String riskLevel, String riskLabel,
                                    String conclusion,
                                    String assessorName, Instant assessedAt,
                                    String submittedByName, Instant submittedAt,
                                    String approvedByName, Instant approvedAt, String approvalOpinion,
                                    String declaredByName, Instant declaredAt, String certificateNo,
                                    CompletionView completion,
                                    List<ReleaseActionView> logs) {
}
