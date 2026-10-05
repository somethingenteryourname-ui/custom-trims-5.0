package dev.customtrims.trim;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The survival items: a smithing template for every new pattern, a "trim essence" for every
 * custom material, and the Prism Template that adds colors and effects in the smithing table.
 */
public final class TrimItems {

    public static final String PRISM = "prism";

    private static final Material TEMPLATE_BASE = Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material PRISM_BASE = Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE;
    private static final Material ESSENCE_BASE = Material.AMETHYST_SHARD;

    /** Vanilla items that are armor trim materials, and the material each one makes. */
    public static final Map<String, String> VANILLA_MATERIAL_ITEMS = Map.ofEntries(
            Map.entry("AMETHYST_SHARD", "amethyst"), Map.entry("COPPER_INGOT", "copper"),
            Map.entry("DIAMOND", "diamond"), Map.entry("EMERALD", "emerald"), Map.entry("GOLD_INGOT", "gold"),
            Map.entry("IRON_INGOT", "iron"), Map.entry("LAPIS_LAZULI", "lapis"),
            Map.entry("NETHERITE_INGOT", "netherite"), Map.entry("QUARTZ", "quartz"),
            Map.entry("REDSTONE", "redstone"), Map.entry("RESIN_BRICK", "resin"));

    private final CustomTrimsPlugin plugin;
    private final NamespacedKey itemKey;
    private final List<NamespacedKey> recipes = new ArrayList<>();
    private final Map<Material, String> prismActions = new EnumMap<>(Material.class);

    public TrimItems(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
        this.itemKey = new NamespacedKey(plugin, "item");
    }

    // ===================================================================== items

    private ItemStack make(Material base, String id, String model, String name, List<String> lore, int amount) {
        ItemStack item = new ItemStack(base, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, id);
        meta.setItemModel(new NamespacedKey(TrimManager.NAMESPACE, model));
        meta.setItemName(name);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack template(String pattern, int amount) {
        List<Color> show = List.of(Color.fromRGB(0x3DF5FF), Color.fromRGB(0xB57CFF), Color.fromRGB(0xFF5FA2));
        return make(TEMPLATE_BASE, "template:" + pattern, "template_" + pattern,
                ColorUtil.gradient(Enums.prettyId(pattern) + " Armor Trim", show),
                List.of("\u00A77Smithing Template",
                        "\u00A78\u2726 \u00A77A brand new trim design",
                        "\u00A79Smithing table: \u00A7fthis + armor + a trim material",
                        "\u00A77Stacks on top of trims already on the armor"), amount);
    }

    public ItemStack essence(String material, int amount) {
        List<Color> colors = plugin.getTrimManager().getCustomMaterials().get(material);
        if (colors == null) colors = List.of(Color.WHITE);
        return make(ESSENCE_BASE, "material:" + material, "essence_" + material,
                ColorUtil.gradient(Enums.prettyId(material) + " Trim Essence", colors),
                List.of("\u00A77Trim Material  " + ColorUtil.swatches(colors),
                        "\u00A79Smithing table: \u00A7fany trim template + armor + this"), amount);
    }

    public ItemStack prism(int amount) {
        List<Color> rainbow = List.of(Color.fromRGB(0xFF2D2D), Color.fromRGB(0xFFF23D), Color.fromRGB(0x3DF5FF), Color.fromRGB(0xB57CFF));
        return make(PRISM_BASE, PRISM, "prism_template", ColorUtil.gradient("Prism Template", rainbow),
                List.of("\u00A77Smithing Template",
                        "\u00A79Smithing table: \u00A7fthis + trimmed armor + ...",
                        "\u00A78\u2022 \u00A7fany dye \u00A77adds a color (up to 8, blended)",
                        "\u00A78\u2022 \u00A7fa sponge \u00A77resets to one color",
                        "\u00A78\u2022 \u00A7fspecial items \u00A77change the aura, trail and style",
                        "\u00A77  (see the list with \u00A7f/ctrim prism\u00A77)"), amount);
    }

    /** "template:nova", "material:fire", "prism" or null for normal items. */
    public String kind(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
    }

    public Map<Material, String> getPrismActions() {
        return prismActions;
    }

    // =================================================================== recipes

    public void registerRecipes() {
        unregisterRecipes();
        ConfigurationSection cfg = plugin.getConfig().getConfigurationSection("smithing");
        if (cfg == null || !cfg.getBoolean("enabled", true)) return;
        TrimManager tm = plugin.getTrimManager();

        prismActions.clear();
        ConfigurationSection pa = cfg.getConfigurationSection("prism-effects");
        if (pa != null) {
            for (String k : pa.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && m.isItem()) prismActions.put(m, pa.getString(k, ""));
                else plugin.getLogger().warning("prism-effects: unknown item " + k);
            }
        }

        List<Material> armor = new ArrayList<>();
        List<Material> dyes = new ArrayList<>();
        List<Material> vanillaTemplates = new ArrayList<>();
        for (Material m : Material.values()) {
            if (m.isLegacy() || !m.isItem()) continue;
            String n = m.name();
            if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")) armor.add(m);
            if (n.endsWith("_DYE")) dyes.add(m);
            if (n.endsWith("_ARMOR_TRIM_SMITHING_TEMPLATE")) vanillaTemplates.add(m);
        }
        List<Material> trimMaterials = new ArrayList<>();
        for (String n : VANILLA_MATERIAL_ITEMS.keySet()) {
            Material m = Material.matchMaterial(n);
            if (m != null) trimMaterials.add(m);
        }

        List<ItemStack> templates = new ArrayList<>();
        for (String p : TrimManager.CUSTOM_PATTERNS) templates.add(template(p, 1));

        // smithing table: lets the custom items go into the slots (the real result is worked out in SmithingListener)
        add(new SmithingTransformRecipe(key("smith_templates"), new ItemStack(Material.LEATHER_HELMET),
                new RecipeChoice.ExactChoice(templates), new RecipeChoice.MaterialChoice(armor),
                new RecipeChoice.MaterialChoice(trimMaterials)));
        List<Material> prismAdds = new ArrayList<>(dyes);
        prismAdds.add(Material.SPONGE);
        for (Material m : prismActions.keySet()) if (!prismAdds.contains(m)) prismAdds.add(m);
        add(new SmithingTransformRecipe(key("smith_prism"), new ItemStack(Material.LEATHER_HELMET),
                new RecipeChoice.ExactChoice(prism(1)), new RecipeChoice.MaterialChoice(armor),
                new RecipeChoice.MaterialChoice(prismAdds)));

        if (!cfg.getBoolean("crafting-recipes", true)) return;

        // crafting: new templates
        ConfigurationSection ti = cfg.getConfigurationSection("template-ingredients");
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            Material k = ti == null ? null : Material.matchMaterial(ti.getString(p, ""));
            if (k == null) continue;
            ShapedRecipe r = new ShapedRecipe(key("template_" + p), template(p, 1));
            r.shape("DKD", "KTK", "DKD");
            r.setIngredient('D', Material.DIAMOND);
            r.setIngredient('K', k);
            r.setIngredient('T', new RecipeChoice.MaterialChoice(vanillaTemplates));
            add(r);
            ShapedRecipe dup = new ShapedRecipe(key("copy_" + p), template(p, 2));
            dup.shape("DTD", "DKD", "DDD");
            dup.setIngredient('D', Material.DIAMOND);
            dup.setIngredient('K', k);
            dup.setIngredient('T', new RecipeChoice.ExactChoice(template(p, 1)));
            add(dup);
        }

        // crafting: trim essences for custom materials
        ConfigurationSection mi = cfg.getConfigurationSection("material-ingredients");
        for (String m : tm.getCustomMaterials().keySet()) {
            Material k = mi == null ? null : Material.matchMaterial(mi.getString(m, ""));
            if (k == null) continue;
            ShapedRecipe r = new ShapedRecipe(key("essence_" + m), essence(m, 2));
            r.shape("KKK", "KAK", "KKK");
            r.setIngredient('K', k);
            r.setIngredient('A', Material.AMETHYST_SHARD);
            add(r);
        }

        // crafting: Prism Template
        ShapedRecipe pr = new ShapedRecipe(key("prism"), prism(2));
        pr.shape(" A ", "GDG", " A ");
        pr.setIngredient('A', Material.AMETHYST_SHARD);
        pr.setIngredient('G', Material.GLOWSTONE_DUST);
        pr.setIngredient('D', Material.DIAMOND);
        add(pr);
    }

    private NamespacedKey key(String name) {
        return new NamespacedKey(plugin, name.toLowerCase(Locale.ROOT));
    }

    private void add(org.bukkit.inventory.Recipe r) {
        try {
            if (Bukkit.addRecipe(r) && r instanceof org.bukkit.Keyed k) recipes.add(k.getKey());
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Could not add a recipe: " + e.getMessage());
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey k : recipes) Bukkit.removeRecipe(k);
        recipes.clear();
    }

    public static boolean isOurRecipe(org.bukkit.inventory.Recipe r, CustomTrimsPlugin plugin) {
        return r instanceof org.bukkit.Keyed k && k.getKey().getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));
    }
}
