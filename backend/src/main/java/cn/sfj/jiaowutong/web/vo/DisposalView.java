package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

/**
 * 违规处置单列表项。时间为 UTC ISO，前端按对象所属司法所时区展示。
 */
public record DisposalView(Long id, String disposalNo,
                           Long objectId, String correctionNo, String maskedName,
                           Long officeId, String officeName, String timezone,
                           String category, String categoryLabel,
                           String status, String statusLabel,
                           String title, String detail,
                           String source, String sourceLabel,
                           int eventCount, Instant firstEventAt, Instant lastEventAt,
                           String registeredByName, Instant registeredAt, Instant resolvedAt) {
}
