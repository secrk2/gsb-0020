package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 把红点事件登记为违规处置单。
 * eventIds 为空时自动收拢该对象该事由下全部未挂单事件。
 */
public record RegisterFromEventsRequest(
        @NotNull(message = "对象不能为空") Long objectId,
        @NotNull(message = "违规事由不能为空") String category,
        List<Long> eventIds,
        @Size(max = 128, message = "标题过长") String title,
        @Size(max = 512, message = "情况说明过长") String detail,
        @Size(min = 4, max = 256, message = "登记理由不少于 4 个字，且不超过 256 字") String reason) {
}
