package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/** 生成/修改解除评估报告：干警填写综合鉴定意见与建议结论 */
public record ReleaseAssessmentRequest(
        @Size(max = 32) String conclusion,
        @Size(max = 512) String opinion) {
}
