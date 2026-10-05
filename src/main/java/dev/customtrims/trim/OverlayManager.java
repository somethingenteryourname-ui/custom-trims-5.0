package dev.customtrims.trim;

import dev.customtrims.CustomTrimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.EquipmentSlot;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Minecraft only lets an item have ONE real armor trim. To stack more, each extra trim is drawn as an
 * extra layer of the armor's look (an "equipment asset" in the resource pack). Every combination that
 * gets used (armor type + extra layers) is remembered here and added to the resource pack.
 */
public final class OverlayManager {

    private final CustomTrimsPlugin plugin;
    private final File file;
    private final Set<String> combos = new TreeSet<>();
    private boolean rebuildScheduled;

    public OverlayManager(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "layer-combos.yml");
        load();
    }

    private void load() {
        combos.clear();
        if (!file.exists()) return;
        combos.addAll(YamlConfiguration.loadConfiguration(file).getStringList("combos"));
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("combos", new ArrayList<>(combos));
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save layer-combos.yml: " + e.getMessage());
        }
    }

    public Set<String> getCombos() {
        return Collections.unmodifiableSet(combos);
    }

    /** Spec string like "netherite|nova:fire|rune:gold". */
    public static String spec(String base, List<Layer> layers) {
        StringBuilder sb = new StringBuilder(base);
        for (Layer l : layers) sb.append('|').append(l.serialize());
        return sb.toString();
    }

    public static String idOf(String spec) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(spec.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("c");
            for (int i = 0; i < 6; i++) sb.append(String.format("%02x", h[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            return "c" + Integer.toHexString(spec.hashCode());
        }
    }

    /** The equipment asset to use for this armor type + layers. New combos are added to the pack. */
    public NamespacedKey assetFor(String base, List<Layer> layers) {
        String spec = spec(base, layers);
        if (combos.add(spec)) {
            save();
            scheduleRebuild();
        }
        return new NamespacedKey(TrimManager.NAMESPACE, idOf(spec));
    }

    public static final List<String> BASES = List.of("leather", "chainmail", "iron", "gold", "diamond", "netherite", "copper", "turtle_scute");

    /** Adds combos ahead of time (e.g. presets) so they're in the pack before anyone uses them. */
    public void preregister(List<Layer> layers) {
        if (layers.isEmpty()) return;
        boolean changed = false;
        for (String base : BASES) changed |= combos.add(spec(base, layers));
        if (changed) save();
    }

    private void scheduleRebuild() {
        if (rebuildScheduled) return;
        rebuildScheduled = true;
        // wait a moment so several new combos (like a full armor set) become one pack update
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            rebuildScheduled = false;
            plugin.rebuildPackAndResend();
        }, 60L);
    }

    /** The vanilla equipment look of an armor item, or null if it can't take extra layers. */
    public static String baseAsset(Material m) {
        String n = m.name();
        if (n.equals("TURTLE_HELMET")) return "turtle_scute";
        if (n.startsWith("LEATHER_")) return "leather";
        if (n.startsWith("CHAINMAIL_")) return "chainmail";
        if (n.startsWith("IRON_")) return "iron";
        if (n.startsWith("GOLDEN_")) return "gold";
        if (n.startsWith("DIAMOND_")) return "diamond";
        if (n.startsWith("NETHERITE_")) return "netherite";
        if (n.startsWith("COPPER_")) return "copper";
        return null;
    }

    public static EquipmentSlot slotOf(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return null;
    }

    /** JSON layers for the vanilla armor look underneath the extra trims. */
    public static String baseLayersJson(String base) {
        if (base.equals("leather")) {
            return "{\"texture\":\"minecraft:leather\",\"dyeable\":{\"color_when_undyed\":-6265536}},"
                    + "{\"texture\":\"minecraft:leather_overlay\"}";
        }
        return "{\"texture\":\"minecraft:" + base + "\"}";
    }
}
