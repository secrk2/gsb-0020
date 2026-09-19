package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotNull;

/**
 * 到期对象生成解除评估报告。
 */
public record GenerateAssessmentRequest(
        @NotNull(message = "对象不能为空") Long objectId) {
}
