package top.ourisland.invertotimer.config.model;

import top.ourisland.invertotimer.runtime.timer.TimeUtil;

import java.time.Duration;
import java.util.Map;

public record ShowcaseConfig(
        boolean enabled,
        Duration startAt,
        Duration interval,
        String text,
        String subtitle,
        String color
) {
    public static ShowcaseConfig fromYaml(final Object obj) {
        if (obj instanceof String s) return new ShowcaseConfig(true, null, null, s, "", "");
        if (!(obj instanceof Map<?, ?> m)) return new ShowcaseConfig(false, null, null, "", "", "");

        Object enObj = m.get("enabled");
        boolean enabled = enObj == null || Boolean.parseBoolean(String.valueOf(enObj));

        Duration startAt = TimeUtil.parseDurationLoose(m.get("start-at"));
        Duration interval = TimeUtil.parseDurationLoose(m.get("interval"));

        Object textObj = m.get("text");
        if (textObj == null) textObj = m.get("title");
        String text = textObj == null ? "" : String.valueOf(textObj);

        Object subObj = m.get("subtitle");
        String subtitle = subObj == null ? "" : String.valueOf(subObj);

        String color = m.get("color") == null ? null : String.valueOf(m.get("color"));

        return new ShowcaseConfig(enabled, startAt, interval, text, subtitle, color);
    }
}
