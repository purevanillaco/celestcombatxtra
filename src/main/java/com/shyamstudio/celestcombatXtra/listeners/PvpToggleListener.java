package com.shyamstudio.celestcombatXtra.listeners;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;
import com.shyamstudio.celestcombatXtra.pvp.PvpToggleManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.List;
import java.util.Locale;

/**
 * Join/quit/kick cache lifecycle and teleport-triggered PVP re-arm.
 */
public class PvpToggleListener implements Listener {

    private final CelestCombatPro plugin;

    public PvpToggleListener(CelestCombatPro plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Async load, kicked off immediately - never blocks this event.
        plugin.getPvpToggleManager().onJoin(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        plugin.getPvpToggleManager().onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerKick(PlayerKickEvent event) {
        // Idempotent - safe alongside onPlayerQuit, since a kick doesn't always
        // guarantee a follow-up quit event.
        plugin.getPvpToggleManager().onQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!plugin.getConfig().getBoolean("pvp.teleport.rearm_enabled", true)) {
            return;
        }

        Player player = event.getPlayer();
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause != null && isExcludedCause(cause)) {
            return;
        }

        PvpToggleManager manager = plugin.getPvpToggleManager();
        manager.triggerTeleportRearm(player);
    }

    private boolean isExcludedCause(PlayerTeleportEvent.TeleportCause cause) {
        List<String> excluded = plugin.getConfig().getStringList("pvp.teleport.excluded_causes");
        for (String entry : excluded) {
            if (entry != null && entry.toUpperCase(Locale.ROOT).equals(cause.name())) {
                return true;
            }
        }
        return false;
    }
}
