package com.shyamstudio.celestcombatXtra.hooks.husksync;

import net.william278.husksync.HuskSync;
import net.william278.husksync.api.BukkitHuskSyncAPI;
import net.william278.husksync.api.HuskSyncAPI;
import net.william278.husksync.data.BukkitData;
import net.william278.husksync.data.DataSnapshot;
import net.william278.husksync.event.BukkitDataSaveEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prevents an inventory-duplication exploit that occurs when HuskSync is
 * installed alongside combat-log punishment: {@code CombatManager.punishCombatLogout}
 * kills the player via {@code setHealth(0)}, which drops their inventory on the
 * ground through normal vanilla death mechanics. Without this hook, HuskSync's
 * own quit-time snapshot can independently capture the player's pre-death, still
 * full inventory (its listener timing relative to ours is not something we
 * control), and re-apply that stale snapshot on the player's next join - on top
 * of the items already dropped, duplicating them.
 *
 * Rather than racing HuskSync's own listener, this hooks {@link BukkitDataSaveEvent},
 * which fires from inside HuskSync's own save pipeline right before a snapshot is
 * persisted - guaranteed to run after any state changes we've already made, no
 * matter how HuskSync's internal listeners are ordered. For any player just killed
 * for combat-logging, the persisted snapshot's health and inventory are zeroed out
 * so the next join doesn't restore items that are already on the ground.
 */
public class HuskSyncHook implements Listener {

    private final CelestCombatPro plugin;
    private final Set<UUID> pendingPunishedPlayers = ConcurrentHashMap.newKeySet();

    public HuskSyncHook(CelestCombatPro plugin) {
        this.plugin = plugin;
    }

    /**
     * Marks a player as pending a combat-log death. Must be called BEFORE
     * {@code player.setHealth(0)} so the marker is in place before any HuskSync
     * quit-time listener (or ours) can react to the disconnect.
     */
    public void markPendingPunishment(Player player) {
        if (player == null) return;
        pendingPunishedPlayers.add(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onDataSave(BukkitDataSaveEvent event) {
        DataSnapshot.SaveCause saveCause = event.getSaveCause();
        if (!saveCause.getDisplayName().equalsIgnoreCase("DISCONNECT")) {
            return;
        }

        UUID uuid = event.getUser().getUuid();
        if (!pendingPunishedPlayers.remove(uuid)) {
            return;
        }

        HuskSyncAPI api = BukkitHuskSyncAPI.getInstance();
        HuskSync huskSync = api.getPlugin();
        event.getData().edit(huskSync, unpacked -> {
            unpacked.getHealth().ifPresent(health -> {
                health.setHealth(0.0D);
                unpacked.setHealth(health);
            });
            unpacked.setInventory(BukkitData.Items.Inventory.empty());
        });

        plugin.debug("Zeroed HuskSync snapshot for combat-logged player " + uuid
                + " to prevent inventory duplication on rejoin.");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Leak guard: discard any stale entry if BukkitDataSaveEvent never fired for
        // this UUID (e.g. HuskSync wasn't actually configured to save on disconnect).
        pendingPunishedPlayers.remove(event.getPlayer().getUniqueId());
    }

    public void cleanup() {
        pendingPunishedPlayers.clear();
    }
}
