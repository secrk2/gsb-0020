package cn.sfj.jiaowutong.web.vo;

import cn.sfj.jiaowutong.domain.DisposalRecord;

import java.time.Instant;
import java.util.List;

/**
 * 违规处置单详情：处置单本体 + 被合并的红点事件 + 逐步操作留痕 + 对象当前档案状态。
 * 对象当前状态用于前端渲染可执行按钮；最终合法与否以后端状态机为准。
 */
public record DisposalDetailView(DisposalView disposal,
                                 String objectStatus, String objectStatusLabel,
                                 List<LinkedEventView> linkedEvents,
                                 List<DisposalActionView> logs) {

    /** 被合并进本处置单的红点事件。 */
    public record LinkedEventView(Long id, String type, String typeLabel,
                                  String detail, Instant eventTime, boolean read) {
    }

    public static DisposalView toView(DisposalRecord r,
                                      String correctionNo, String maskedName,
                                      String officeName, String timezone) {
        return new DisposalView(
                r.getId(), r.getDisposalNo(),
                r.getOffender().getId(), correctionNo, maskedName,
                r.getOfficeId(), officeName, timezone,
                r.getCategory().name(), r.getCategory().getLabel(),
                r.getStatus().name(), r.getStatus().getLabel(),
                r.getTitle(), r.getDetail(),
                r.getSource(), "AUTO".equals(r.getSource()) ? "事件合成" : "手工登记",
                r.getEventCount(), r.getFirstEventAt(), r.getLastEventAt(),
                r.getRegisteredByName(), r.getRegisteredAt(), r.getResolvedAt());
    }
}
