package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 处置推进请求：训诫 / 收监 / 驳回 / 撤销，均须填写理由并留痕。
 * action 取值：ADMONISH / REIMPRISON / REJECT / REVOKE。
 */
public record DisposalActionRequest(
        @Size(min = 4, max = 256, message = "处置理由不少于 4 个字，且不超过 256 字") String reason) {
}
