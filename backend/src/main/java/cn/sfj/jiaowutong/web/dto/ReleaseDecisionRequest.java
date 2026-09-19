package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/** 评估审批/重新提交/执行解除时的理由说明（审批驳回必填） */
public record ReleaseDecisionRequest(
        @Size(max = 512) String reason) {
}
