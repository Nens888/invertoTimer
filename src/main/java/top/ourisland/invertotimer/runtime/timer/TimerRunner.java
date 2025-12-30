package top.ourisland.invertotimer.runtime.timer;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import org.slf4j.Logger;
import top.ourisland.invertotimer.InvertoTimer;
import top.ourisland.invertotimer.config.ConfigManager;
import top.ourisland.invertotimer.config.model.GlobalConfig;
import top.ourisland.invertotimer.config.model.TimerConfig;

import java.time.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class TimerRunner {
    private final InvertoTimer plugin;
    private final ProxyServer proxy;
    private final Logger logger;
    private final ConfigManager configs;

    private final Map<String, TimerInstance> instances = new HashMap<>();

    private volatile GlobalConfig global;
    private volatile Map<String, TimerConfig> timerConfigs;
    private ScheduledTask tickTask;

    public TimerRunner(final InvertoTimer plugin, final ProxyServer proxy, final Logger logger, final ConfigManager configs) {
        this.plugin = plugin;
        this.proxy = proxy;
        this.logger = logger;
        this.configs = configs;
        reloadFromConfig();
    }

    public synchronized void reloadFromConfig() {
        instances.values().forEach(TimerInstance::dispose);

        this.global = configs.getGlobalConfig();
        this.timerConfigs = configs.getTimers();

        instances.clear();
        for (String id : timerConfigs.keySet()) {
            instances.put(id, new TimerInstance(plugin, proxy, logger, timerConfigs.get(id), global.zoneId()));
        }
    }

    public synchronized void start() {
        if (tickTask != null) tickTask.cancel();
        tickTask = proxy.getScheduler()
                .buildTask(plugin, this::tick)
                .repeat(250, TimeUnit.MILLISECONDS)
                .schedule();
    }

    private void tick() {
        final Instant now = Instant.now();
        final GlobalConfig g = this.global;

        for (TimerInstance inst : instances.values()) {
            inst.tick(now, g);
        }
    }

    public String peekNextOccurrence(final String timerId) {
        TimerInstance inst = instances.get(timerId);
        return inst == null ? null : inst.peekNext();
    }

    @Subscribe
    public void onPostLogin(final PostLoginEvent e) {
        final Player p = e.getPlayer();
        for (TimerInstance inst : instances.values()) inst.refreshFor(p);
    }

    @Subscribe
    public void onServerConnected(final ServerConnectedEvent e) {
        final Player p = e.getPlayer();
        for (TimerInstance inst : instances.values()) inst.refreshFor(p);
    }

    @Subscribe
    public void onDisconnect(final DisconnectEvent e) {
        final Player p = e.getPlayer();
        for (TimerInstance inst : instances.values()) inst.hideFor(p);
    }

}
