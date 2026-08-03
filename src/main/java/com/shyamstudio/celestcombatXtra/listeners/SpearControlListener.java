package com.shyamstudio.celestcombatXtra.listeners;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.inventory.ItemStack;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;
import com.shyamstudio.celestcombatXtra.Scheduler;
import com.shyamstudio.celestcombatXtra.cooldown.ItemCooldownManager;
import com.shyamstudio.celestcombatXtra.cooldown.ItemCooldownManager.CooldownKey;
import com.shyamstudio.celestcombatXtra.cooldown.UseCooldowns;

import net.md_5.bungee.api.chat.TextComponent;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spear (1.21+) lunge cooldown and optional damage disable.
 */
public final class SpearControlListener implements Listener {

  private static final Set<Material> SPEAR_MATERIALS = resolveSpearMaterials();
  private static final long COOLDOWN_ACTION_BAR_INTERVAL = 20L;

  private final CelestCombatPro plugin;
  private final ItemCooldownManager cooldownManager;
  private final Map<UUID, Scheduler.Task> spearCountdownTasks = new ConcurrentHashMap<>();

  public SpearControlListener(CelestCombatPro plugin, ItemCooldownManager cooldownManager) {
    this.plugin = plugin;
    this.cooldownManager = cooldownManager;
  }

  private static Set<Material> resolveSpearMaterials() {
    Set<Material> spears = EnumSet.noneOf(Material.class);
    for (Material m : Material.values()) {
      if (m.isAir()) continue;
      String name = m.name();
      if ("SPEAR".equals(name) || name.endsWith("_SPEAR")) {
        spears.add(m);
      }
    }
    Material legacy = Material.matchMaterial("SPEAR");
    if (legacy != null) spears.add(legacy);
    return spears;
  }

  private static boolean isSpear(Material material) {
    return material != null && SPEAR_MATERIALS.contains(material);
  }

  private static CooldownKey lungeKey(Material material) {
    return new CooldownKey(material, "lunge");
  }

  private boolean master() {
    return plugin.getConfig().getBoolean("spear_control.enabled", false);
  }

  private String bypass() {
    return plugin.getConfig().getString("spear_control.bypass_permission", "celestcombatxtra.bypass.spear_control");
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onSpearInteract(PlayerInteractEvent event) {
    if (SPEAR_MATERIALS.isEmpty()) return;
    if (event.getHand() != EquipmentSlot.HAND) return;
    if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

    Player player = event.getPlayer();
    ItemStack item = event.getItem();
    if (item == null || !isSpear(item.getType())) return;

    Material spearMaterial = item.getType();
    CooldownKey lungeKey = lungeKey(spearMaterial);

    // disable_spears: block all spear use (right-click) - works even when spear_control.enabled is false
    if (plugin.getConfig().getBoolean("spear_control.disable_spears", false)) {
      if (player.hasPermission(bypass())) return;
      event.setCancelled(true);
      event.setUseItemInHand(Event.Result.DENY);
      if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
        event.setUseInteractedBlock(Event.Result.DENY);
      }
      notifySpearDisabled(player);
      return;
    }

    if (!master()) return;
    // when right-clicking block, deny block interaction so spear receives it
    if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
      event.setUseInteractedBlock(Event.Result.DENY);
    }

    if (!plugin.getConfig().getBoolean("spear_control.lunge_cooldown.enabled", true)) return;
    if (player.hasPermission(bypass())) return;

    if (cooldownManager.isGeneralItemOnCooldown(player, lungeKey)) {
      event.setCancelled(true);
      event.setUseItemInHand(Event.Result.DENY);
      int remaining = cooldownManager.getRemainingGeneralItemCooldown(player, lungeKey);
      int rt = cooldownManager.getRemainingGeneralItemCooldownTicks(player, lungeKey);
      if (rt > 0) {
        player.setCooldown(spearMaterial, rt);
      }
      UseCooldowns.applyUseCooldownIfKnown(player, spearMaterial);
      String bar = plugin.getLanguageManager().getActionBar("spear_lunge_cooldown",
          Map.of("time", String.valueOf(remaining)));
      if (bar != null) {
        plugin.sendActionBar(player, TextComponent.fromLegacyText(bar));
      }
      return;
    }

    String durStr = plugin.getConfig().getString("spear_control.lunge_cooldown.duration", "1s");
    long durationTicks = plugin.getTimeFormatter().parseTimeToTicks(durStr, 20L);
    long ms = Math.max(0L, durationTicks * 50L);
    if (ms <= 0) return;

    // start cooldown immediately so physical overlay blocks spam (like wind charge / pearls)
    cooldownManager.startGeneralCooldown(player, lungeKey, ms);
    startSpearCooldownActionBar(player, lungeKey);
  }

  private void startSpearCooldownActionBar(Player player, CooldownKey lungeKey) {
    if (player == null) return;
    if (plugin.isActionBarDisabled()) return;
    UUID uuid = player.getUniqueId();
    Scheduler.Task existing = spearCountdownTasks.get(uuid);
    if (existing != null) existing.cancel();

    Scheduler.Task task = Scheduler.runTaskTimer(() -> {
      if (!player.isOnline()) {
        spearCountdownTasks.remove(uuid);
        return;
      }
      if (!cooldownManager.isGeneralItemOnCooldown(player, lungeKey)) {
        Scheduler.Task t = spearCountdownTasks.remove(uuid);
        if (t != null) t.cancel();
        return;
      }
      int remaining = cooldownManager.getRemainingGeneralItemCooldown(player, lungeKey);
      int rt = cooldownManager.getRemainingGeneralItemCooldownTicks(player, lungeKey);
      if (rt > 0 && lungeKey.material() != null) {
        player.setCooldown(lungeKey.material(), rt);
      }
      String bar = plugin.getLanguageManager().getActionBar("spear_lunge_cooldown",
          Map.of("time", String.valueOf(remaining)));
      if (bar != null) {
        plugin.sendActionBar(player, TextComponent.fromLegacyText(bar));
      }
    }, 0L, COOLDOWN_ACTION_BAR_INTERVAL);
    spearCountdownTasks.put(uuid, task);
  }

  public void shutdown() {
    spearCountdownTasks.values().forEach(Scheduler.Task::cancel);
    spearCountdownTasks.clear();
  }

  /** Cancels spear arm swing (lunge/jab) when disable_spears is true. */
  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onSpearArmSwing(PlayerArmSwingEvent event) {
    if (SPEAR_MATERIALS.isEmpty()) return;
    if (!plugin.getConfig().getBoolean("spear_control.disable_spears", false)) return;

    Player player = event.getPlayer();
    ItemStack main = player.getInventory().getItemInMainHand();
    ItemStack off = player.getInventory().getItemInOffHand();
    boolean hasSpear = (main != null && isSpear(main.getType()))
        || (off != null && isSpear(off.getType()));
    if (!hasSpear) return;
    if (player.hasPermission(bypass())) return;

    event.setCancelled(true);
    notifySpearDisabled(player);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void onSpearDamage(EntityDamageByEntityEvent event) {
    if (SPEAR_MATERIALS.isEmpty()) return;
    boolean disableDamage = plugin.getConfig().getBoolean("spear_control.disable_damage", false);
    boolean disableSpears = plugin.getConfig().getBoolean("spear_control.disable_spears", false);
    // disable_spears works independently; disable_damage requires spear_control.enabled
    boolean shouldBlock = disableSpears || (disableDamage && master());
    if (!shouldBlock) return;

    if (!isSpearDamage(event)) return;

    Player attacker = resolveAttacker(event);
    if (attacker != null && attacker.hasPermission(bypass())) return;

    event.setCancelled(true);
  }

  private Player resolveAttacker(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player p) {
      return p;
    }
    if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
      return p;
    }
    return null;
  }

  private boolean isSpearDamage(EntityDamageByEntityEvent event) {
    if (event.getDamager() instanceof Player p) {
      ItemStack hand = p.getInventory().getItemInMainHand();
      return isSpear(hand.getType());
    }
    if (event.getDamager() instanceof Projectile proj) {
      return proj.getType().name().contains("SPEAR");
    }
    return false;
  }

  private void notifySpearDisabled(Player player) {
    if (player == null) return;
    String bar = plugin.getLanguageManager().getActionBar("spear_disabled", Map.of());
    if (bar != null) {
      plugin.sendActionBar(player, TextComponent.fromLegacyText(bar));
    } else {
      plugin.getMessageService().sendMessage(player, "spear_disabled", Map.of());
    }
  }
}
