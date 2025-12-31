package top.ourisland.invertotimer.showcase;

import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import top.ourisland.invertotimer.runtime.I18n;
import top.ourisland.invertotimer.runtime.RuntimeContext;

import java.util.Locale;
import java.util.function.Supplier;

public class BossbarShowcase implements Showcase {
    private final RuntimeContext ctx;
    private final String text;
    private final BossBar bossBar;
    private final Supplier<Float> progressSupplier;

    public BossbarShowcase(
            RuntimeContext ctx,
            String text,
            String colorRaw,
            Supplier<Float> progressSupplier
    ) {
        this.ctx = ctx;
        this.text = text;
        this.progressSupplier = progressSupplier;

        BossBar.Color color = parseColor(colorRaw);

        this.bossBar = BossBar.bossBar(
                Component.empty(),
                1.0f,
                color,
                BossBar.Overlay.PROGRESS
        );
    }

    private static BossBar.Color parseColor(String s) {
        if (s == null) return BossBar.Color.BLUE;
        String v = s.trim().toLowerCase(Locale.ROOT);
        return switch (v) {
            case "pink" -> BossBar.Color.PINK;
            case "red" -> BossBar.Color.RED;
            case "green" -> BossBar.Color.GREEN;
            case "yellow" -> BossBar.Color.YELLOW;
            case "purple" -> BossBar.Color.PURPLE;
            case "white" -> BossBar.Color.WHITE;
            default -> BossBar.Color.BLUE;
        };
    }

    @Override
    public String name() {
        return "bossbar";
    }

    @Override
    public String description() {
        return I18n.langStrNP("itimer.showcase.bossbar.desc");
    }

    @Override
    public void show() {
        bossBar.name(ctx.render(text));

        float p = progressSupplier.get();
        if (p < 0f) p = 0f;
        if (p > 1f) p = 1f;
        bossBar.progress(p);

        for (Player p0 : ctx.players()) {
            if (!ctx.allowed(p0)) {
                p0.hideBossBar(bossBar);
                continue;
            }
            p0.showBossBar(bossBar);
        }
    }

    public void showTo(Player p) {
        if (!ctx.allowed(p)) return;
        bossBar.name(ctx.render(text));
        bossBar.progress(progressSupplier.get());
        p.showBossBar(bossBar);
    }

    public void hideFrom(Player p) {
        p.hideBossBar(bossBar);
    }
}
