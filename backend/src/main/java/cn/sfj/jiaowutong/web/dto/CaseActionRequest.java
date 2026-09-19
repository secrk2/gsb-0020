package cn.sfj.jiaowutong.web.dto;

import jakarta.validation.constraints.Size;

/**
 * 违规处置动作请求：ADMONISH 训诫 / REIMPRISON 收监 / REJECT 驳回 / REVOKE 撤销。
 * 理由必填并随动作永久留痕。
 */
public record CaseActionRequest(
        @Size(min = 4, max = 256, message = "处置理由不少于 4 个字") String reason) {
}
