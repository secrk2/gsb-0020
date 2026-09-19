package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 违规处置登记。eventId 可空：可由原始红点登记，也可凭事由直接登记。
 * 事由可空（由系统按类型生成摘要）；填写时不少于 4 字。
 */
public record RegisterCaseRequest(
        @NotNull(message = "矫正对象不能为空") Long offenderId,
        @NotNull(message = "违规事由类型不能为空") String reasonType,
        @Size(max = 256, message = "登记事由过长") String reason,
        Long eventId) {
}
