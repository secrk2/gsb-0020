package cn.sfj.jiaowutong.domain;

/**
 * 违规处置事由分类，同时作为“同一对象同一事由时间窗只合成一条”的去重键。
 * 与红点事件类型对齐（越界/禁区/未报到），另留 OTHER 供干警登记其他违规。
 */
public enum DisposalCategory {
    GEOFENCE_BREACH("越界"),
    FORBIDDEN_ZONE("禁区闯入"),
    ABSENT("未按日报到"),
    OTHER("其他违规");

    private final String label;

    DisposalCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** 红点事件类型 → 处置事由；无法归类的返回 OTHER。 */
    public static DisposalCategory fromEventType(String eventType) {
        return switch (eventType) {
            case "GEOFENCE_BREACH" -> GEOFENCE_BREACH;
            case "FORBIDDEN_ZONE" -> FORBIDDEN_ZONE;
            case "ABSENT" -> ABSENT;
            default -> OTHER;
        };
    }
}
