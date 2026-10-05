package dev.customtrims.pack;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.OverlayManager;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import dev.customtrims.util.Palettes;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.World;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Builds the two packs that make new trims show up on armor:
 * - a data pack (in the main world's datapacks folder) that registers the new patterns + materials
 * - a resource pack (sent to players) with the textures and color palettes
 */
public final class PackBuilder {

    private static final int DATA_PACK_FORMAT = 94;     // Minecraft 1.21.11
    private static final int RESOURCE_PACK_FORMAT = 75; // Minecraft 1.21.11

    private static final List<String> VANILLA_PALETTES = List.of(
            "amethyst", "copper", "copper_darker", "diamond", "diamond_darker", "emerald", "gold", "gold_darker",
            "iron", "iron_darker", "lapis", "netherite", "netherite_darker", "quartz", "redstone", "resin");

    private final CustomTrimsPlugin plugin;
    private byte[] resourcePack;
    private byte[] sha1;
    private String sha1Hex = "";
    private boolean restartNeeded;

    public PackBuilder(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    public byte[] getResourcePack() {
        return resourcePack;
    }

    public byte[] getSha1() {
        return sha1;
    }

    public String getSha1Hex() {
        return sha1Hex;
    }

    /** True if the data pack changed and the server must restart once for it to load. */
    public boolean isRestartNeeded() {
        return restartNeeded;
    }

    public void build() {
        TrimManager tm = plugin.getTrimManager();
        try {
            if (writeDataPack(tm)) restartNeeded = true;
        } catch (IOException e) {
            plugin.getLogger().severe("Could not write the data pack: " + e.getMessage());
        }
        try {
            resourcePack = buildResourcePack(tm);
            sha1 = MessageDigest.getInstance("SHA-1").digest(resourcePack);
            StringBuilder sb = new StringBuilder();
            for (byte b : sha1) sb.append(String.format("%02x", b));
            sha1Hex = sb.toString();
            File out = new File(plugin.getDataFolder(), "CustomTrims-ResourcePack.zip");
            Files.write(out.toPath(), resourcePack);
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Could not build the resource pack: " + e.getMessage());
        }
        if (restartNeeded) {
            plugin.getLogger().warning("==============================================================");
            plugin.getLogger().warning(" CustomTrims installed/updated its data pack.");
            plugin.getLogger().warning(" RESTART the server once so the new trims and materials load!");
            plugin.getLogger().warning("==============================================================");
        }
    }

    // ================================================================ data pack

    /** @return true if anything was written (changed). */
    private boolean writeDataPack(TrimManager tm) throws IOException {
        List<World> worlds = Bukkit.getWorlds();
        if (worlds.isEmpty()) throw new IOException("no world loaded");
        File root = new File(new File(worlds.get(0).getWorldFolder(), "datapacks"), "CustomTrims");

        Map<String, String> files = new TreeMap<>();
        files.put("pack.mcmeta", "{\n  \"pack\": {\n    \"description\": \"CustomTrims: new armor trims\",\n"
                + "    \"min_format\": " + DATA_PACK_FORMAT + ",\n    \"max_format\": " + DATA_PACK_FORMAT + "\n  }\n}\n");

        for (String p : TrimManager.CUSTOM_PATTERNS) {
            files.put("data/" + TrimManager.NAMESPACE + "/trim_pattern/" + p + ".json",
                    "{\n  \"asset_id\": \"" + TrimManager.NAMESPACE + ":" + p + "\",\n  \"decal\": false,\n"
                            + "  \"description\": { \"text\": \"" + Enums.prettyId(p) + " Armor Trim\" }\n}\n");
        }
        for (Map.Entry<String, List<Color>> e : tm.getCustomMaterials().entrySet()) {
            String id = e.getKey();
            Color mid = ColorUtil.sample(e.getValue(), 0.5);
            files.put("data/" + TrimManager.NAMESPACE + "/trim_material/" + id + ".json",
                    "{\n  \"asset_name\": \"" + assetName(id) + "\",\n"
                            + "  \"description\": { \"text\": \"" + Enums.prettyId(id) + " Material\", \"color\": \""
                            + ColorUtil.hex(mid) + "\" }\n}\n");
        }

        // Note: old material files are never deleted on purpose. Removing a material that
        // players' armor still uses could break those items.
        boolean changed = false;
        for (Map.Entry<String, String> e : files.entrySet()) {
            File f = new File(root, e.getKey());
            byte[] bytes = e.getValue().getBytes(StandardCharsets.UTF_8);
            if (f.exists() && Arrays.equals(Files.readAllBytes(f.toPath()), bytes)) continue;
            File parent = f.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) throw new IOException("can't create " + parent);
            Files.write(f.toPath(), bytes);
            changed = true;
        }
        return changed;
    }

    static String assetName(String materialId) {
        return "ct_" + materialId;
    }

    // ============================================================ resource pack

    private byte[] buildResourcePack(TrimManager tm) throws IOException {
        Map<String, byte[]> files = new TreeMap<>();
        String ns = TrimManager.NAMESPACE;

        files.put("pack.mcmeta", ("{\n  \"pack\": {\n    \"description\": \"CustomTrims armor trims\",\n"
                + "    \"min_format\": " + RESOURCE_PACK_FORMAT + ",\n    \"max_format\": " + RESOURCE_PACK_FORMAT + "\n  }\n}\n")
                .getBytes(StandardCharsets.UTF_8));

        // pattern textures (grayscale, recolored by the game for each material)
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            files.put("assets/" + ns + "/textures/trims/entity/humanoid/" + p + ".png", resource("pack/patterns/" + p + "_humanoid.png"));
            files.put("assets/" + ns + "/textures/trims/entity/humanoid_leggings/" + p + ".png", resource("pack/patterns/" + p + "_humanoid_leggings.png"));
        }

        // color palettes for custom materials
        for (Map.Entry<String, List<Color>> e : tm.getCustomMaterials().entrySet()) {
            files.put("assets/" + ns + "/textures/trims/color_palettes/" + e.getKey() + ".png", palettePng(e.getValue()));
        }

        files.put("assets/minecraft/atlases/armor_trims.json", atlasJson(tm).getBytes(StandardCharsets.UTF_8));

        // liquid aura/trail models and animated textures
        String index = new String(resource("pack/static/index.txt"), StandardCharsets.UTF_8);
        for (String line : index.split("\\R")) {
            String path = line.trim();
            if (!path.isEmpty()) files.put(path, resource("pack/static/" + path));
        }

        addStackedTrims(files, tm);
        addItemIcons(files, tm);

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            FileTime fixed = FileTime.fromMillis(315532800000L); // fixed time so the pack hash stays the same
            for (Map.Entry<String, byte[]> e : files.entrySet()) {
                ZipEntry entry = new ZipEntry(e.getKey());
                entry.setLastModifiedTime(fixed);
                zip.putNextEntry(entry);
                zip.write(e.getValue());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private String atlasJson(TrimManager tm) {
        String ns = TrimManager.NAMESPACE;
        List<String> customPerms = new ArrayList<>();
        for (String id : tm.getCustomMaterials().keySet()) {
            customPerms.add("\"" + assetName(id) + "\": \"" + ns + ":trims/color_palettes/" + id + "\"");
        }
        List<String> vanillaPerms = new ArrayList<>();
        for (String v : VANILLA_PALETTES) {
            vanillaPerms.add("\"" + v + "\": \"minecraft:trims/color_palettes/" + v + "\"");
        }

        List<String> customTextures = new ArrayList<>();
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            customTextures.add("\"" + ns + ":trims/entity/humanoid/" + p + "\"");
            customTextures.add("\"" + ns + ":trims/entity/humanoid_leggings/" + p + "\"");
        }
        List<String> vanillaTextures = new ArrayList<>();
        for (String p : TrimManager.VANILLA_PATTERNS) {
            vanillaTextures.add("\"minecraft:trims/entity/humanoid/" + p + "\"");
            vanillaTextures.add("\"minecraft:trims/entity/humanoid_leggings/" + p + "\"");
        }

        List<String> allPerms = new ArrayList<>(vanillaPerms);
        allPerms.addAll(customPerms);

        StringBuilder sb = new StringBuilder("{\n  \"sources\": [\n");
        // new patterns x every material
        sb.append(source(customTextures, allPerms));
        // vanilla patterns x the new materials
        if (!customPerms.isEmpty()) sb.append(",\n").append(source(vanillaTextures, customPerms));
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String source(List<String> textures, List<String> perms) {
        return "    {\n      \"type\": \"minecraft:paletted_permutations\",\n"
                + "      \"palette_key\": \"minecraft:trims/color_palettes/trim_palette\",\n"
                + "      \"permutations\": {\n        " + String.join(",\n        ", perms) + "\n      },\n"
                + "      \"textures\": [\n        " + String.join(",\n        ", textures) + "\n      ]\n    }";
    }

    private static byte[] palettePng(List<Color> colors) throws IOException {
        int[] slots = Palettes.fromColors(colors);
        BufferedImage img = new BufferedImage(8, 1, BufferedImage.TYPE_INT_RGB);
        for (int i = 0; i < 8; i++) img.setRGB(i, 0, slots[i]);
        return png(img);
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private int[] paletteOf(TrimManager tm, String material) {
        List<Color> custom = tm.getCustomMaterials().get(material);
        if (custom != null) return Palettes.fromColors(custom);
        int[] v = Palettes.vanilla(material);
        return v != null ? v : Palettes.fromColors(List.of(Color.WHITE));
    }

    /** Recolors a grayscale trim texture with a palette (what the game does for normal trims). */
    private static BufferedImage recolor(BufferedImage src, int[] palette) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                if (a == 0) continue;
                int gray = argb & 0xFF;
                int best = 0;
                for (int i = 1; i < 8; i++) {
                    if (Math.abs(Palettes.KEY[i] - gray) < Math.abs(Palettes.KEY[best] - gray)) best = i;
                }
                out.setRGB(x, y, (a << 24) | (palette[best] & 0xFFFFFF));
            }
        }
        return out;
    }

    private BufferedImage image(String path) throws IOException {
        return ImageIO.read(new java.io.ByteArrayInputStream(resource(path)));
    }

    // ===================================================== stacked trims (extra layers)

    private void addStackedTrims(Map<String, byte[]> files, TrimManager tm) throws IOException {
        String ns = TrimManager.NAMESPACE;
        java.util.Set<String> overlays = new java.util.TreeSet<>();
        for (String spec : plugin.getOverlays().getCombos()) {
            String[] parts = spec.split("\\|");
            if (parts.length < 2) continue;
            String base = parts[0];
            StringBuilder extra = new StringBuilder();
            for (int i = 1; i < parts.length; i++) {
                String[] pm = parts[i].split(":");
                if (pm.length != 2 || !TrimManager.CUSTOM_PATTERNS.contains(pm[0])) continue;
                String tex = "ov_" + pm[0] + "_" + pm[1];
                overlays.add(pm[0] + ":" + pm[1]);
                extra.append(",{\"texture\":\"").append(ns).append(':').append(tex).append("\"}");
            }
            String layers = OverlayManager.baseLayersJson(base) + extra;
            String json = "{\"layers\":{\"humanoid\":[" + layers + "],\"humanoid_leggings\":[" + layers + "]}}";
            files.put("assets/" + ns + "/equipment/" + OverlayManager.idOf(spec) + ".json", json.getBytes(StandardCharsets.UTF_8));
        }
        for (String pm : overlays) {
            String[] parts = pm.split(":");
            int[] palette = paletteOf(tm, parts[1]);
            for (String layer : new String[]{"humanoid", "humanoid_leggings"}) {
                BufferedImage src = image("pack/patterns/" + parts[0] + "_" + layer + ".png");
                files.put("assets/" + ns + "/textures/entity/equipment/" + layer + "/ov_" + parts[0] + "_" + parts[1] + ".png",
                        png(recolor(src, palette)));
            }
        }
    }

    // ====================================================== item icons for survival items

    private void addItemIcons(Map<String, byte[]> files, TrimManager tm) throws IOException {
        int[] show = Palettes.fromColors(List.of(Color.fromRGB(0x3DF5FF), Color.fromRGB(0xB57CFF), Color.fromRGB(0xFF5FA2)));
        for (String p : TrimManager.CUSTOM_PATTERNS) {
            BufferedImage pat = recolor(image("pack/patterns/" + p + "_humanoid.png"), show);
            addIcon(files, "template_" + p, templateIcon(pat));
        }
        for (Map.Entry<String, List<Color>> e : tm.getCustomMaterials().entrySet()) {
            addIcon(files, "essence_" + e.getKey(), gemIcon(Palettes.fromColors(e.getValue())));
        }
        addIcon(files, "prism_template", prismIcon());
        addArmorIcons(files);
    }

    /**
     * Inventory look for trimmed armor: the normal armor icon, trim accents tinted with the trim's
     * colors, and (in the inventory only) an animated liquid ring around it.
     */
    private void addArmorIcons(Map<String, byte[]> files) {
        String ns = TrimManager.NAMESPACE;
        List<String> layers = new ArrayList<>(List.of("icon_ring_back_a", "icon_ring_back_b", "icon_ring_back_shine",
                "icon_ring_front_a", "icon_ring_front_b", "icon_ring_front_shine"));
        for (String slot : new String[]{"helmet", "chestplate", "leggings", "boots"}) {
            layers.add("icon_trim_" + slot + "_a");
            layers.add("icon_trim_" + slot + "_b");
        }
        for (String l : layers) {
            files.put("assets/" + ns + "/models/item/" + l + ".json",
                    ("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + ns + ":item/" + l + "\"}}")
                            .getBytes(StandardCharsets.UTF_8));
        }
        boolean ring = plugin.getConfig().getBoolean("inventory-ring", true);
        for (org.bukkit.Material m : org.bukkit.Material.values()) {
            if (m.isLegacy() || !m.isItem() || !TrimManager.isIconArmor(m)) continue;
            String name = m.name().toLowerCase(java.util.Locale.ROOT);
            String slot = name.endsWith("_helmet") ? "helmet" : name.endsWith("_chestplate") ? "chestplate"
                    : name.endsWith("_leggings") ? "leggings" : "boots";
            String base = "{\"type\":\"minecraft:model\",\"model\":\"minecraft:item/" + name + "\""
                    + (name.startsWith("leather_") ? ",\"tints\":[{\"type\":\"minecraft:dye\",\"default\":-6265536}]" : "") + "}";
            String trimA = layer(ns, "icon_trim_" + slot + "_a", 0);
            String trimB = layer(ns, "icon_trim_" + slot + "_b", 1);
            String plain = "{\"type\":\"minecraft:composite\",\"models\":[" + base + "," + trimA + "," + trimB + "]}";
            String withRing = "{\"type\":\"minecraft:composite\",\"models\":["
                    + layer(ns, "icon_ring_back_a", 2) + "," + layer(ns, "icon_ring_back_b", 3) + "," + layer(ns, "icon_ring_back_shine", -1) + ","
                    + base + "," + trimA + "," + trimB + ","
                    + layer(ns, "icon_ring_front_a", 2) + "," + layer(ns, "icon_ring_front_b", 3) + "," + layer(ns, "icon_ring_front_shine", -1) + "]}";
            String json = ring
                    ? "{\"model\":{\"type\":\"minecraft:select\",\"property\":\"minecraft:display_context\",\"cases\":[{\"when\":\"gui\",\"model\":"
                      + withRing + "}],\"fallback\":" + plain + "}}"
                    : "{\"model\":" + plain + "}";
            files.put("assets/" + ns + "/items/" + TrimManager.iconModel(m) + ".json", json.getBytes(StandardCharsets.UTF_8));
        }
    }

    /** One tinted (or untinted when tint < 0) layer model inside a composite. */
    private static String layer(String ns, String model, int tint) {
        String m = "{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/" + model + "\"";
        if (tint >= 0) m += ",\"tints\":[{\"type\":\"minecraft:custom_model_data\",\"index\":" + tint + ",\"default\":-1}]";
        return m + "}";
    }

    private static void addIcon(Map<String, byte[]> files, String name, BufferedImage img) throws IOException {
        String ns = TrimManager.NAMESPACE;
        files.put("assets/" + ns + "/textures/item/" + name + ".png", png(img));
        files.put("assets/" + ns + "/models/item/" + name + ".json",
                ("{\"parent\":\"minecraft:item/generated\",\"textures\":{\"layer0\":\"" + ns + ":item/" + name + "\"}}")
                        .getBytes(StandardCharsets.UTF_8));
        files.put("assets/" + ns + "/items/" + name + ".json",
                ("{\"model\":{\"type\":\"minecraft:model\",\"model\":\"" + ns + ":item/" + name + "\"}}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    /** A smithing-template-shaped tablet with a little picture of the pattern on it. */
    private static BufferedImage templateIcon(BufferedImage pattern) {
        BufferedImage img = tablet(0xFF3A3F4A, 0xFF6B7280, 0xFF1E2127);
        // the chestplate front of the pattern texture (8x12) shrunk onto the tablet
        for (int y = 0; y < 10; y++) {
            for (int x = 0; x < 8; x++) {
                int argb = pattern.getRGB(20 + x, 20 + Math.min(11, (int) Math.round(y * 1.2)));
                if ((argb >>> 24) != 0) img.setRGB(4 + x, 3 + y, argb | 0xFF000000);
            }
        }
        return img;
    }

    private static BufferedImage prismIcon() {
        BufferedImage img = tablet(0xFF2F2A3F, 0xFF8E7CC3, 0xFF17131F);
        int[] rainbow = {0xFF2D2D, 0xFF8C1A, 0xFFF23D, 0x7CFF3A, 0x3DF5FF, 0x2D5BFF, 0xB57CFF};
        for (int y = 3; y < 13; y++) {
            for (int x = 4; x < 12; x++) {
                if ((x + y) % 2 == 0 || x == 4 || x == 11) continue;
                img.setRGB(x, y, 0xFF000000 | rainbow[((x + y) / 2) % rainbow.length]);
            }
        }
        img.setRGB(7, 7, 0xFFFFFFFF);
        img.setRGB(8, 8, 0xFFFFFFFF);
        return img;
    }

    private static BufferedImage tablet(int fill, int light, int dark) {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 1; y < 15; y++) {
            for (int x = 2; x < 14; x++) {
                boolean corner = (y == 1 || y == 14) && (x == 2 || x == 13);
                if (corner) continue;
                int c = fill;
                if (y == 1 || x == 2) c = light;
                if (y == 14 || x == 13) c = dark;
                img.setRGB(x, y, c);
            }
        }
        return img;
    }

    /** A shiny gem in the material's colors. */
    private static BufferedImage gemIcon(int[] pal) {
        String[] shape = {
                "................",
                "................",
                ".....######.....",
                "....#aabbbc#....",
                "...#aabbbbcc#...",
                "..#aabbbbbbccd#.",
                "..############..",
                "...#bbbbbcccd#..",
                "....#bbbcccd#...",
                ".....#bcccd#....",
                "......#ccd#.....",
                ".......#d#......",
                "........#.......",
                "................",
                "................",
                "................"};
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                char ch = shape[y].charAt(x);
                int c = switch (ch) {
                    case '#' -> pal[6];
                    case 'a' -> pal[0];
                    case 'b' -> pal[1];
                    case 'c' -> pal[3];
                    case 'd' -> pal[5];
                    default -> -1;
                };
                if (c != -1) img.setRGB(x, y, 0xFF000000 | (c & 0xFFFFFF));
            }
        }
        img.setRGB(6, 4, 0xFFFFFFFF);
        img.setRGB(5, 5, 0xFFFFFFFF);
        return img;
    }

    private byte[] resource(String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) throw new IOException("missing " + path + " in plugin jar");
            return in.readAllBytes();
        }
    }
}
