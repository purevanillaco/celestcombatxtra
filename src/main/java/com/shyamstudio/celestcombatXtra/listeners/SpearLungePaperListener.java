package com.shyamstudio.celestcombatXtra.listeners;

import io.papermc.paper.event.entity.EntityLungeEvent;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import com.shyamstudio.celestcombatXtra.util.SpearMaterials;

/**
 * 26.x spear lunge hook. only loaded on servers with EntityLungeEvent.
 */
public final class SpearLungePaperListener implements Listener {

  private final SpearControlListener spearControl;

  public SpearLungePaperListener(SpearControlListener spearControl) {
    this.spearControl = spearControl;
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void onEntityLunge(EntityLungeEvent event) {
    if (!(event.getEntity() instanceof Player player)) return;

    ItemStack main = player.getInventory().getItemInMainHand();
    Material spearMaterial = main != null ? main.getType() : null;
    if (!SpearMaterials.isSpear(spearMaterial)) return;

    if (spearControl.handleLungeAttempt(player, spearMaterial)) {
      event.setCancelled(true);
    }
  }
}
