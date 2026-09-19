package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 解除流程动作请求：提交 / 审批通过 / 退回补正 / 重新提交 / 宣告解除。
 * 审批与宣告的意见/理由必填（不少于 4 字），由服务端按动作校验。
 */
public record ReleaseReasonRequest(
        @Size(max = 256, message = "意见/理由过长") String reason) {
}
