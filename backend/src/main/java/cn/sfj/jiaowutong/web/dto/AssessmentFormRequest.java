package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 评估报告内容（草稿填写 / 退回补正）。
 */
public record AssessmentFormRequest(
        @Min(value = 0, message = "综合评分不能低于 0") @Max(value = 100, message = "综合评分不能高于 100")
        Integer score,
        @Size(max = 512, message = "教育情况描述过长") String education,
        @Size(max = 512, message = "监管合规描述过长") String compliance,
        @Size(max = 512, message = "认罪悔罪描述过长") String repentance,
        String riskLevel,
        @Size(max = 512, message = "评估结论过长") String conclusion) {
}
