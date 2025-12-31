package top.ourisland.invertotimer.runtime.showcase;

import top.ourisland.invertotimer.config.model.ShowcaseConfig;
import top.ourisland.invertotimer.runtime.RuntimeContext;
import top.ourisland.invertotimer.showcase.*;

import java.util.Locale;
import java.util.function.Supplier;

public final class ShowcaseFactory {
    private ShowcaseFactory() {
    }

    public static Showcase create(
            final String key,
            final ShowcaseConfig sc,
            final RuntimeContext ctx,
            final Supplier<Float> bossbarProgress
    ) {
        if (key == null || sc == null || !sc.enabled()) return null;

        return switch (key.toLowerCase(Locale.ROOT)) {
            case "actionbar" -> new ActionbarShowcase(ctx, sc.text());
            case "text" -> new TextShowcase(ctx, sc.text());
            case "title" -> new TitleShowcase(ctx, sc.text(), sc.subtitle());
            case "bossbar" -> new BossbarShowcase(ctx, sc.text(), sc.color(), bossbarProgress);
            default -> null;
        };
    }
}
