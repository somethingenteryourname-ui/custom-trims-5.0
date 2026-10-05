package dev.customtrims.trim;

import dev.customtrims.CustomTrimsPlugin;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

import java.util.List;
import java.util.Locale;

/**
 * Survival support:
 * - Smithing table: new templates and trim essences add trims (stacking up to max-trim-layers),
 *   vanilla templates keep custom data instead of glitching it, the Prism Template adds colors/effects.
 * - Grindstone: removes all custom trims from a piece.
 * - Crafting: stops the custom items from being used as plain amethyst / vanilla templates.
 */
public final class SmithingListener implements Listener {

    private final CustomTrimsPlugin plugin;

    public SmithingListener(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    private TrimManager tm() {
        return plugin.getTrimManager();
    }

    private TrimItems items() {
        return plugin.getTrimItems();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        if (!plugin.getConfig().getBoolean("smithing.enabled", true)) return;
        SmithingInventory inv = event.getInventory();
        ItemStack template = inv.getInputTemplate();
        ItemStack armor = inv.getInputEquipment();
        ItemStack addition = inv.getInputMineral();
        String kind = items().kind(template);
        boolean ours = kind != null && (kind.equals(TrimItems.PRISM) || kind.startsWith("template:"));
        if (template == null || armor == null || addition == null) {
            if (ours) event.setResult(null);
            return;
        }
        if (!TrimManager.isArmorPiece(armor) || armor.getType() == Material.ELYTRA) {
            if (ours) event.setResult(null);
            return;
        }
        TrimSettings current = tm().read(armor);
        double height = TrimManager.heightOf(armor.getType());

        // ---------------------------------------------------------------- Prism Template
        if (TrimItems.PRISM.equals(kind)) {
            TrimSettings s = current != null ? current : tm().freshSettings(armor);
            String error = prism(s, addition, current == null);
            if (error != null) {
                event.setResult(null);
                return;
            }
            event.setResult(result(armor, s, height));
            return;
        }

        String pattern = null;
        if (kind != null && kind.startsWith("template:")) pattern = kind.substring("template:".length());
        else if (kind == null) pattern = vanillaPattern(template.getType());

        // ---------------------------------------------------------------- not a trim template
        if (pattern == null) {
            if (ours) {
                event.setResult(null);
                return;
            }
            // e.g. netherite upgrade: rebuild the custom look for the new armor type
            ItemStack r = event.getResult();
            if (current != null && r != null && !r.getType().isAir()) {
                ItemStack fixed = r.clone();
                tm().apply(fixed, current, TrimManager.heightOf(fixed.getType()));
                event.setResult(fixed);
            }
            return;
        }

        // ---------------------------------------------------------------- trim templates
        String material = materialOf(addition);
        if (material == null) {
            event.setResult(null);
            return;
        }
        boolean customMaterial = tm().getCustomMaterials().containsKey(material);
        if (kind == null && current == null && !customMaterial) return;   // plain vanilla trim: leave it alone

        TrimSettings s = current != null ? current : tm().freshSettings(armor);
        if (current == null) s.setColors(tm().colorsFor(material));
        tm().addTrim(s, pattern, material);
        event.setResult(result(armor, s, height));
    }

    private ItemStack result(ItemStack armor, TrimSettings s, double height) {
        ItemStack r = armor.clone();
        r.setAmount(1);
        tm().apply(r, s, height);
        return r;
    }

    /** Applies a Prism Template addition. Returns an error or null. */
    private String prism(TrimSettings s, ItemStack addition, boolean fresh) {
        Material m = addition.getType();
        String n = m.name();
        if (n.endsWith("_DYE")) {
            try {
                DyeColor dye = DyeColor.valueOf(n.substring(0, n.length() - 4));
                if (fresh) s.setColors(List.of(dye.getColor()));
                else TrimEdits.addColor(s, dye.getColor());
                return null;
            } catch (IllegalArgumentException e) {
                return "unknown dye";
            }
        }
        if (m == Material.SPONGE) {
            s.setColors(List.of(s.primary()));
            return null;
        }
        String action = items().getPrismActions().get(m);
        if (action == null || action.isBlank()) return "nothing";
        return TrimEdits.action(tm(), s, action);
    }

    /** Which trim material an addition item gives, or null. */
    private String materialOf(ItemStack addition) {
        String kind = items().kind(addition);
        if (kind != null) {
            if (kind.startsWith("material:")) {
                String m = kind.substring("material:".length());
                return tm().getCustomMaterials().containsKey(m) ? m : null;
            }
            return null;
        }
        return TrimItems.VANILLA_MATERIAL_ITEMS.get(addition.getType().name());
    }

    private static String vanillaPattern(Material template) {
        String n = template.name();
        if (!n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) return null;
        String p = n.substring(0, n.length() - "_ARMOR_TRIM_SMITHING_TEMPLATE".length()).toLowerCase(Locale.ROOT);
        return TrimManager.VANILLA_PATTERNS.contains(p) ? p : null;
    }

    // -------------------------------------------------------------------- grindstone

    @EventHandler(priority = EventPriority.HIGH)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        if (!plugin.getConfig().getBoolean("smithing.grindstone-removes-trims", true)) return;
        GrindstoneInventory inv = event.getInventory();
        ItemStack top = inv.getUpperItem();
        ItemStack bottom = inv.getLowerItem();
        ItemStack only = top != null && bottom == null ? top : (bottom != null && top == null ? bottom : null);
        if (only == null || tm().read(only) == null) return;
        ItemStack base = event.getResult() != null && !event.getResult().getType().isAir() ? event.getResult() : only;
        ItemStack cleaned = base.clone();
        cleaned.setAmount(1);
        tm().clear(cleaned);
        event.setResult(cleaned);
    }

    // ---------------------------------------------------------------------- crafting

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null || TrimItems.isOurRecipe(event.getRecipe(), plugin)) return;
        for (ItemStack it : event.getInventory().getMatrix()) {
            if (items().kind(it) != null) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }
}
