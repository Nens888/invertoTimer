package top.ourisland.invertotimer.runtime.timer;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import top.ourisland.invertotimer.InvertoTimer;
import top.ourisland.invertotimer.action.Action;
import top.ourisland.invertotimer.config.model.ActionConfig;
import top.ourisland.invertotimer.config.model.GlobalConfig;
import top.ourisland.invertotimer.config.model.ShowcaseConfig;
import top.ourisland.invertotimer.config.model.TimerConfig;
import top.ourisland.invertotimer.runtime.RuntimeContext;
import top.ourisland.invertotimer.runtime.action.ActionFactory;
import top.ourisland.invertotimer.runtime.showcase.ShowcaseFactory;
import top.ourisland.invertotimer.runtime.showcase.ShowcaseSlot;
import top.ourisland.invertotimer.runtime.showcase.ShowcaseType;
import top.ourisland.invertotimer.showcase.BossbarShowcase;
import top.ourisland.invertotimer.showcase.Showcase;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

final class TimerInstance {
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final InvertoTimer plugin;

    private final ProxyServer proxy;
    private final Logger logger;

    private final TimerConfig cfg;
    private final ZoneId zoneId;

    private final List<ScheduledTask> actionTasks = new ArrayList<>();

    private final Map<String, ShowcaseSlot> showcaseSlots = new HashMap<>();

    private Instant expireAt = Instant.EPOCH;

    private Cron5 cron;
    private LocalDateTime oneTime;
    private ZonedDateTime nextTarget;

    private volatile GlobalConfig lastGlobal;
    private volatile Instant lastNow;

    private BossbarShowcase bossbarShowcase;
    private ShowcaseConfig bossBarConfig;

    private RuntimeContext ctx;

    TimerInstance(
            final InvertoTimer plugin,
            final ProxyServer proxy,
            final Logger logger,
            final TimerConfig cfg,
            final ZoneId zoneId
    ) {
        this.plugin = Objects.requireNonNull(plugin);
        this.proxy = proxy;
        this.logger = logger;
        this.cfg = cfg;
        this.zoneId = zoneId;

        parseTimeSpec();
        buildRuntimeContext();
        rebuildForNewTarget();
    }

    private void parseTimeSpec() {
        try {
            if (cfg.cron() != null && !cfg.cron().isBlank()) {
                this.cron = Cron5.parse(cfg.cron());
            }
        } catch (Exception ignored) {
            this.cron = null;
        }

        try {
            if (cfg.time() != null && !cfg.time().isBlank()) {
                this.oneTime = LocalDateTime.parse(cfg.time(), TIME_FMT);
            }
        } catch (Exception ignored) {
            this.oneTime = null;
        }
    }

    private void buildRuntimeContext() {
        this.ctx = new RuntimeContext(
                proxy,
                this::isPlayerAllowedUsingLastGlobal,
                s -> applyPlaceholders(s, lastNow == null ? Instant.now() : lastNow)
        );
    }

    private void rebuildForNewTarget() {
        cancelActionTasks();

        showcaseSlots.clear();
        bossbarShowcase = null;
        bossBarConfig = null;

        expireAt = Instant.EPOCH;

        if (nextTarget == null) return;

        final Instant targetInstant = nextTarget.toInstant();
        expireAt = targetInstant;

        for (ActionConfig ac : cfg.actions()) {
            final Instant at = targetInstant.plus(ac.shift());
            final Action action = ActionFactory.create(ac, ctx);
            if (action == null) continue;

            scheduleAction(at, action);
            if (at.isAfter(expireAt)) expireAt = at;
        }

        for (Map.Entry<String, ShowcaseConfig> e : cfg.showcases().entrySet()) {
            final String key = e.getKey();
            final ShowcaseConfig sc = e.getValue();
            if (sc == null || !sc.enabled()) continue;

            ShowcaseType kind = ShowcaseType.fromKey(key);
            if (kind == null) continue;

            Showcase showcase = ShowcaseFactory.create(
                    key, sc, ctx,
                    () -> progressFor(sc, lastNow == null ? Instant.now() : lastNow)
            );
            if (showcase == null) continue;

            ShowcaseSlot slot = new ShowcaseSlot(kind, sc, showcase);
            showcaseSlots.put(key.toLowerCase(Locale.ROOT), slot);

            if (showcase instanceof BossbarShowcase bbs) {
                bossbarShowcase = bbs;
                bossBarConfig = sc;
            }
        }
    }

    private boolean isPlayerAllowedUsingLastGlobal(final Player p) {
        final GlobalConfig g = lastGlobal;
        if (g == null) return true;
        return isPlayerAllowed(p, g);
    }

    private String applyPlaceholders(final String text, final Instant now) {
        final long remainingSec = nextTarget == null ? 0 :
                Math.max(0, Duration.between(now, nextTarget.toInstant()).getSeconds());

        long days = remainingSec / 86400;
        long rem = remainingSec % 86400;
        long hours = rem / 3600;
        rem %= 3600;
        long minutes = rem / 60;
        long seconds = rem % 60;

        String out = text == null ? "" : text;
        out = out.replace("{id}", cfg.id());
        out = out.replace("{description}", cfg.description());
        out = out.replace("{remaining}", TimeUtil.formatHMS(remainingSec));
        out = out.replace("{days}", String.valueOf(days));
        out = out.replace("{hours}", String.valueOf(hours));
        out = out.replace("{minutes}", String.valueOf(minutes));
        out = out.replace("{seconds}", String.valueOf(seconds));
        out = out.replace("{total_seconds}", String.valueOf(remainingSec));
        if (nextTarget != null) out = out.replace("{target}", nextTarget.toString());
        return out;
    }

    private void cancelActionTasks() {
        for (ScheduledTask t : actionTasks) {
            try {
                t.cancel();
            } catch (Exception ignored) {
            }
        }
        actionTasks.clear();
    }

    private void scheduleAction(final Instant at, final Action action) {
        long delayMs = at.toEpochMilli() - System.currentTimeMillis();
        if (delayMs < 0) delayMs = 0;

        ScheduledTask task = proxy.getScheduler()
                .buildTask(plugin, () -> {
                    // ensure placeholders use near-real execution time
                    lastNow = Instant.now();
                    try {
                        action.execute();
                    } catch (Exception e) {
                        logger.error("Failed executing action {} for timer {}", action.name(), cfg.id(), e);
                    }
                })
                .delay(delayMs, TimeUnit.MILLISECONDS)
                .schedule();

        actionTasks.add(task);
    }

    private float progressFor(final ShowcaseConfig sc, final Instant now) {
        if (nextTarget == null) return 1.0f;
        final Instant target = nextTarget.toInstant();
        final Duration startAt = sc.startAt();

        if (startAt != null && !startAt.isZero()) {
            long total = startAt.abs().getSeconds();
            long rem = Math.max(0, Duration.between(now, target).getSeconds());
            float p = total == 0 ? 0f : (rem / (float) total);
            if (p < 0f) p = 0f;
            if (p > 1f) p = 1f;
            return p;
        }

        long rem = Math.max(0, Duration.between(now, target).getSeconds());
        return rem > 0 ? 1.0f : 0.0f;
    }

    private boolean isPlayerAllowed(final Player p, final GlobalConfig global) {
        final String serverName = p.getCurrentServer()
                .map(c -> c.getServerInfo().getName())
                .orElse(null);

        if (!global.limitation().isAllowed(serverName)) return false;
        return cfg.limitation().isAllowed(serverName);
    }

    void tick(final Instant now, final GlobalConfig global) {
        this.lastNow = now;
        this.lastGlobal = global;

        ensureNextTarget(now);
        updateShowcases(now);
    }

    void ensureNextTarget(final Instant now) {
        if (nextTarget == null) {
            nextTarget = computeNextTarget(now);
            rebuildForNewTarget();
            return;
        }

        if (now.isAfter(getExpireTime())) {
            nextTarget = computeNextTarget(now);
            rebuildForNewTarget();
        }
    }

    private void updateShowcases(final Instant now) {
        if (nextTarget == null) return;

        final long nowMs = now.toEpochMilli();

        for (ShowcaseSlot slot : showcaseSlots.values()) {
            if (!shouldShow(slot.config(), now)) continue;

            Duration interval = slot.config().interval();
            if (interval == null) interval = slot.kind().defaultInterval();

            if (!slot.tryAcquire(nowMs, interval.toMillis())) continue;

            try {
                slot.showcase().show();
            } catch (Exception e) {
                logger.error("Failed showing {} for timer {}", slot.showcase().name(), cfg.id(), e);
            }
        }
    }

    private ZonedDateTime computeNextTarget(final Instant now) {
        final ZonedDateTime zNow = ZonedDateTime.ofInstant(now, zoneId).withSecond(0).withNano(0);
        if (oneTime != null) {
            final ZonedDateTime target = oneTime.atZone(zoneId);
            return target.isAfter(zNow) ? target : null;
        }
        if (cron != null) {
            return cron.nextAfter(zNow);
        }
        return null;
    }

    private Instant getExpireTime() {
        return expireAt.plusSeconds(2);
    }

    private boolean shouldShow(final ShowcaseConfig sc, final Instant now) {
        if (nextTarget == null) return false;
        if (sc.startAt() == null) return true;

        final Instant target = nextTarget.toInstant();
        final Instant begin = target.minus(sc.startAt().abs());
        return !now.isBefore(begin);
    }

    String peekNext() {
        return nextTarget == null ? null : nextTarget.toString();
    }

    void refreshFor(final Player p) {
        if (bossbarShowcase == null || bossBarConfig == null) return;
        final Instant now = lastNow == null ? Instant.now() : lastNow;

        if (nextTarget == null) return;
        if (!shouldShow(bossBarConfig, now)) {
            bossbarShowcase.hideFrom(p);
            return;
        }

        if (!isPlayerAllowedUsingLastGlobal(p)) {
            bossbarShowcase.hideFrom(p);
            return;
        }

        bossbarShowcase.showTo(p);
    }

    void hideFor(final Player p) {
        if (bossbarShowcase != null) bossbarShowcase.hideFrom(p);
    }

    void dispose() {
        cancelActionTasks();
        if (bossbarShowcase != null) {
            for (Player p : proxy.getAllPlayers()) bossbarShowcase.hideFrom(p);
        }
    }
}
