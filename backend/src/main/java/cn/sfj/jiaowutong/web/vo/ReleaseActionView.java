package cn.sfj.jiaowutong.web.vo;

import java.time.Instant;

public record ReleaseActionView(Long id, String action, String actionLabel,
                                String toStage, String toStageLabel,
                                String operatorName, String reason, String detail,
                                Instant operatedAt) {
}
