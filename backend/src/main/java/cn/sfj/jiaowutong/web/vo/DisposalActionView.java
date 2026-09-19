package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

/**
 * 违规处置操作留痕条目（只追加，不可改）。
 */
public record DisposalActionView(Long id, String action, String actionLabel,
                                 String toStatus, String toStatusLabel,
                                 String operatorName, String reason, String detail,
                                 Instant operatedAt) {
}
