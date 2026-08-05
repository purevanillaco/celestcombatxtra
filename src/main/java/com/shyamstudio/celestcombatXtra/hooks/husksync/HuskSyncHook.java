package com.shyamstudio.celestcombatXtra.hooks.husksync;

import net.william278.husksync.HuskSync;
import net.william278.husksync.api.BukkitHuskSyncAPI;
import net.william278.husksync.api.HuskSyncAPI;
import net.william278.husksync.data.BukkitData;
import net.william278.husksync.data.DataSnapshot;
import net.william278.husksync.event.BukkitDataSaveEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;
import com.shyamstudio.celestcombatXtra.Scheduler;

import java.util.ArrayList;
import java.util.List;
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
 *
 * With HuskSync installed, the normal vanilla death-drop triggered by
 * {@code player.setHealth(0)} during quit handling is not reliable (HuskSync's own
 * quit-time processing appears to race with / interfere with the live player state
 * before the vanilla death sequence runs), so this hook takes over item disposal
 * entirely: it captures the player's full inventory itself, clears it, and manually
 * drops the items on the ground - the same approach HuskSync-aware combat-log
 * plugins (e.g. CombatLogX's HuskSync expansion) use, rather than trusting the
 * vanilla drop to happen during a quit.
 */
public class HuskSyncHook implements Listener {

    private static final long DROP_DELAY_TICKS = 1L;

    private final CelestCombatPro plugin;
    private final Set<UUID> pendingPunishedPlayers = ConcurrentHashMap.newKeySet();

    public HuskSyncHook(CelestCombatPro plugin) {
        this.plugin = plugin;
    }

    /**
     * Marks a player as pending a combat-log death, captures and clears their
     * live inventory, and schedules the items to be dropped on the ground. Must
     * be called BEFORE {@code player.setHealth(0)} so the marker is in place
     * before any HuskSync quit-time listener (or ours) can react to the
     * disconnect, and so the captured inventory is the pre-death state.
     */
    public void markPendingPunishment(Player player) {
        if (player == null) return;
        pendingPunishedPlayers.add(player.getUniqueId());

        PlayerInventory inventory = player.getInventory();
        List<ItemStack> drops = new ArrayList<>();

        ItemStack[] contents = inventory.getContents();
        collectAndClear(contents, drops);
        inventory.setContents(contents);

        ItemStack[] armor = inventory.getArmorContents();
        collectAndClear(armor, drops);
        inventory.setArmorContents(armor);

        ItemStack offHand = inventory.getItemInOffHand();
        if (!isEmpty(offHand)) {
            drops.add(offHand);
        }
        inventory.setItemInOffHand(null);

        if (drops.isEmpty()) {
            return;
        }

        Location dropLocation = player.getLocation().clone();
        Scheduler.runLocationTaskLater(dropLocation, () -> {
            World world = dropLocation.getWorld();
            if (world == null) return;
            for (ItemStack item : drops) {
                world.dropItemNaturally(dropLocation, item);
            }
        }, DROP_DELAY_TICKS);
    }

    private void collectAndClear(ItemStack[] slots, List<ItemStack> drops) {
        for (int i = 0; i < slots.length; i++) {
            if (!isEmpty(slots[i])) {
                drops.add(slots[i]);
                slots[i] = null;
            }
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType() == Material.AIR;
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
