package com.shyamstudio.celestcombatXtra.hooks.protection;

import me.angeschossen.lands.api.LandsIntegration;
import me.angeschossen.lands.api.land.Area;
import me.angeschossen.lands.api.player.LandPlayer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import com.shyamstudio.celestcombatXtra.CelestCombatPro;
import com.shyamstudio.celestcombatXtra.combat.CombatManager;

/**
 * Lands-plugin-backed claim_protection - one of two selectable claim-system
 * backends (see claim_protection.backend in config.yml), the other being
 * {@link GriefPreventionHook}. All barrier rendering/push-back/cleanup is shared
 * in {@link ClaimProtectionHook}; this class only knows how to ask Lands whether
 * a player is blocked from a location.
 *
 * Lands has no direct equivalent of GriefPrevention's tiered ClaimPermission enum
 * (MANAGE/ACCESS/EDIT/BUILD/CONTAINER) - claim_protection.required_permission only
 * applies to the GriefPrevention backend. This uses Lands' own purpose-built
 * {@code Area#canEnter(LandPlayer, boolean)} check instead, the direct semantic
 * match for "is this player allowed past the barrier".
 */
public class LandsHook extends ClaimProtectionHook {

    private final LandsIntegration landsIntegration;

    public LandsHook(CelestCombatPro plugin, CombatManager combatManager) {
        super(plugin, combatManager);
        this.landsIntegration = LandsIntegration.of(plugin);
    }

    @Override
    protected boolean checkClaimProtection(Location location, Player player) {
        try {
            Area area = landsIntegration.getArea(location);
            if (area == null) {
                return false;
            }

            LandPlayer landPlayer = landsIntegration.getLandPlayer(player.getUniqueId());
            if (landPlayer == null) {
                plugin.debug("No LandPlayer found for " + player.getName() + " while checking Lands claim - treating as unprotected");
                return false;
            }

            return !area.canEnter(landPlayer, false);
        } catch (Exception e) {
            plugin.getLogger().warning("Error checking Lands claim: " + e.getMessage());
            return false; // Default to not protected if there's an error
        }
    }
}
