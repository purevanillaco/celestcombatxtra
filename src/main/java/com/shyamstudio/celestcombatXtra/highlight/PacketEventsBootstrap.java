package com.shyamstudio.celestcombatXtra.highlight;

import com.github.retrooper.packetevents.PacketEvents;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * PacketEvents is shaded into this plugin's jar (relocated), but bootstrapping it
 * can still fail on some environments (e.g. an unsupported protocol version, an
 * incompatible server fork). PacketEvents is only required for the PVP status
 * highlight (glow) feature - if it fails to load/initialize, we simply skip that
 * feature instead of failing the whole plugin. Everything else (toggle, warmups,
 * damage gating, teleport re-arm) works without it.
 */
public final class PacketEventsBootstrap {
    private static boolean loaded = false;
    private static boolean available = false;

    private PacketEventsBootstrap() {}

    /** Call from JavaPlugin#onLoad(). */
    public static void tryLoad(JavaPlugin plugin) {
        try {
            PacketEvents.setAPI(SpigotPacketEventsBuilder.build(plugin));
            PacketEvents.getAPI().getSettings().checkForUpdates(false).bStats(false);
            PacketEvents.getAPI().load();
            loaded = true;
        } catch (Throwable t) {
            loaded = false;
            plugin.getLogger().warning("PacketEvents failed to load - the PVP status highlight "
                    + "feature will be disabled, everything else works normally. Cause: " + t);
        }
    }

    /** Call from JavaPlugin#onEnable(), before constructing PvpHighlightManager. */
    public static void tryInit(JavaPlugin plugin) {
        if (!loaded) {
            return;
        }
        try {
            PacketEvents.getAPI().init();
            available = true;
        } catch (Throwable t) {
            available = false;
            plugin.getLogger().warning("PacketEvents failed to initialize - the PVP status highlight "
                    + "feature will be disabled, everything else works normally. Cause: " + t);
        }
    }

    /** Call from JavaPlugin#onDisable(), after all managers using it have shut down. */
    public static void tryTerminate() {
        if (!available) {
            return;
        }
        try {
            PacketEvents.getAPI().terminate();
        } catch (Throwable ignored) {
            // best-effort on shutdown
        } finally {
            available = false;
            loaded = false;
        }
    }

    /** True only once PacketEvents has loaded and initialized successfully. */
    public static boolean isAvailable() {
        return available;
    }
}
