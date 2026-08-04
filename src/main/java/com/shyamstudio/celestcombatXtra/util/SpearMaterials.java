package com.shyamstudio.celestcombatXtra.util;

import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

public final class SpearMaterials {

  private static final Set<Material> ALL = resolve();

  private SpearMaterials() {}

  public static Set<Material> all() {
    return ALL;
  }

  public static boolean isSpear(Material material) {
    return material != null && ALL.contains(material);
  }

  private static Set<Material> resolve() {
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
}
