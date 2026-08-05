package com.shyamstudio.celestcombatXtra.hooks.protection;

import me.ryanhamshire.GriefPrevention.Claim;
import me.ryanhamshire.GriefPrevention.ClaimPermission;
import me.ryanhamshire.GriefPrevention.GriefPrevention;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;
import com.shyamstudio.celestcombatXtra.combat.CombatManager;

import java.util.function.Supplier;

/**
 * GriefPrevention-backed claim_protection - one of two selectable claim-system
 * backends (see claim_protection.backend in config.yml), the other being
 * {@link LandsHook}. All barrier rendering/push-back/cleanup is shared in
 * {@link ClaimProtectionHook}; this class only knows how to ask GriefPrevention
 * whether a player is blocked from a location.
 */
public class GriefPreventionHook extends ClaimProtectionHook {

    private ClaimPermission requiredPermission;

    public GriefPreventionHook(CelestCombatPro plugin, CombatManager combatManager) {
        super(plugin, combatManager);
    }

    @Override
    protected void reloadBackendConfig() {
        this.requiredPermission = loadRequiredPermission();
    }

    private ClaimPermission loadRequiredPermission() {
        String permissionName = plugin.getConfig().getString("claim_protection.required_permission", "BUILD");

        try {
            ClaimPermission permission;
            switch (permissionName.toUpperCase()) {
                case "MANAGE":
                    permission = ClaimPermission.Manage;
                    break;
                case "ACCESS":
                    permission = ClaimPermission.Access;
                    break;
                case "EDIT":
                    permission = ClaimPermission.Edit;
                    break;
                case "BUILD":
                    permission = ClaimPermission.Build;
                    break;
                case "CONTAINER":
                    permission = ClaimPermission.Inventory;
                    break;
                default:
                    plugin.getLogger().warning("Invalid claim permission '" + permissionName + "' in config. Using BUILD instead.");
                    plugin.getLogger().warning("Valid permissions are: MANAGE, ACCESS, EDIT, BUILD, INVENTORY");
                    return ClaimPermission.Build;
            }

            plugin.debug("Using claim permission: " + permission.name() + " for protection checks.");
            return permission;

        } catch (Exception e) {
            plugin.getLogger().warning("Error loading claim permission '" + permissionName + "': " + e.getMessage());
            plugin.getLogger().warning("Valid permissions are: MANAGE, ACCESS, EDIT, BUILD, INVENTORY");
            return ClaimPermission.Build;
        }
    }

    @Override
    protected boolean checkClaimProtection(Location location, Player player) {
        try {
            Claim claim = GriefPrevention.instance.dataStore.getClaimAt(location, false, null);
            if (claim == null) {
                return false;
            }

            Supplier<String> permissionResult = claim.checkPermission(player, requiredPermission, null);
            return permissionResult != null; // null means allowed, any string means denied
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking GriefPrevention claim: " + e.getMessage());
            return false; // Default to not protected if there's an error
        }
    }
}
