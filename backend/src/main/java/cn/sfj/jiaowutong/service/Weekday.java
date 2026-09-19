package cn.sfj.jiaowutong.service;

/**
 * 星期（DayOfWeek.name()）→ 中文标签。
 */
public final class Weekday {

    private Weekday() {
    }

    public static String label(String dayOfWeek) {
        if (dayOfWeek == null) {
            return "—";
        }
        return switch (dayOfWeek) {
            case "MONDAY" -> "周一";
            case "TUESDAY" -> "周二";
            case "WEDNESDAY" -> "周三";
            case "THURSDAY" -> "周四";
            case "FRIDAY" -> "周五";
            case "SATURDAY" -> "周六";
            case "SUNDAY" -> "周日";
            default -> dayOfWeek;
        };
    }
}
