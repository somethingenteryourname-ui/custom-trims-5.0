package dev.customtrims.trim;

import dev.customtrims.effect.AuraShape;
import dev.customtrims.effect.EffectStyle;
import dev.customtrims.effect.ParticleStyle;
import dev.customtrims.effect.TrailShape;
import dev.customtrims.util.ColorUtil;
import dev.customtrims.util.Enums;
import org.bukkit.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything about one custom trim. Stored on the armor item as a string. */
public final class TrimSettings {

    public static final int MAX_COLORS = 8;

    /** Used when an item has no style saved (set from config default-style). */
    public static EffectStyle defaultStyle = EffectStyle.LIQUID;

    public String name = "Custom";
    public String pattern = "silence";
    public String material = "diamond";
    /** 1 to 8 colors, blended together in order. */
    public List<Color> colors = new ArrayList<>(List.of(Color.fromRGB(0x00E5FF), Color.fromRGB(0xB000FF)));
    public ParticleStyle trailParticle = ParticleStyle.GRADIENT;
    public TrailShape trailShape = TrailShape.COMET;
    public AuraShape auraShape = AuraShape.RING;
    public ParticleStyle auraParticle = ParticleStyle.BLEND;
    public double size = 1.0;
    public double density = 1.0;
    public boolean glint = true;
    public EffectStyle style = defaultStyle;
    /** Extra trims layered on top of the main one (new patterns only). */
    public List<Layer> layers = new ArrayList<>();
    /** Speed of the effects (color flow, spinning, bobbing). */
    public double speed = 1.0;
    /** Moves the aura up or down, in blocks. */
    public double height = 0.0;
    /** Cycle mode: off, presets, random, patterns, colors, auras, trails. */
    public String cycle = "off";
    /** Seconds between changes when cycling. */
    public double cycleSeconds = 5.0;
    /** While cycling: on = the aura changes too, keep = keep your aura, off = no aura. */
    public String cycleAura = "on";
    /** Where the cycle is up to. */
    public int cycleIndex = 0;

    public Color primary() {
        return colors.get(0);
    }

    public Color secondary() {
        return colors.get(colors.size() - 1);
    }

    /** Smooth blend through all colors, t from 0 to 1. */
    public Color sample(double t) {
        return ColorUtil.sample(colors, t);
    }

    /** Steps through the colors one by one (wraps around). */
    public Color cycle(int index) {
        return colors.get(Math.floorMod(index, colors.size()));
    }

    public void setColors(List<Color> list) {
        if (list == null || list.isEmpty()) return;
        colors = new ArrayList<>(list.subList(0, Math.min(MAX_COLORS, list.size())));
    }

    public TrimSettings copy() {
        TrimSettings s = new TrimSettings();
        s.name = name;
        s.pattern = pattern;
        s.material = material;
        s.colors = new ArrayList<>(colors);
        s.trailParticle = trailParticle;
        s.trailShape = trailShape;
        s.auraShape = auraShape;
        s.auraParticle = auraParticle;
        s.size = size;
        s.density = density;
        s.glint = glint;
        s.style = style;
        s.layers = new ArrayList<>(layers);
        s.speed = speed;
        s.height = height;
        s.cycle = cycle;
        s.cycleSeconds = cycleSeconds;
        s.cycleIndex = cycleIndex;
        s.cycleAura = cycleAura;
        return s;
    }

    public static String cleanName(String raw) {
        String s = ColorUtil.strip(raw == null ? "" : raw).replace(";", "").replace("=", "").trim();
        if (s.isEmpty()) s = "Custom";
        if (s.length() > 24) s = s.substring(0, 24);
        return s;
    }

    public String serialize() {
        StringBuilder hex = new StringBuilder();
        for (Color c : colors) {
            if (hex.length() > 0) hex.append(',');
            hex.append(Integer.toHexString(c.asRGB()));
        }
        return "v=2"
                + ";name=" + name
                + ";pattern=" + pattern
                + ";material=" + material
                + ";colors=" + hex
                + ";trail=" + trailParticle.name()
                + ";trailShape=" + trailShape.name()
                + ";aura=" + auraShape.name()
                + ";auraParticle=" + auraParticle.name()
                + ";size=" + size
                + ";density=" + density
                + ";glint=" + glint
                + ";style=" + style.name()
                + ";layers=" + layersString()
                + ";speed=" + speed
                + ";height=" + height
                + ";cycle=" + cycle
                + ";cycleSeconds=" + cycleSeconds
                + ";cycleIndex=" + cycleIndex
                + ";cycleAura=" + cycleAura;
    }

    private String layersString() {
        StringBuilder sb = new StringBuilder();
        for (Layer l : layers) {
            if (sb.length() > 0) sb.append(',');
            sb.append(l.serialize());
        }
        return sb.toString();
    }

    /** True if this pattern is already used anywhere on the piece. */
    public boolean hasPattern(String p) {
        if (pattern.equals(p)) return true;
        for (Layer l : layers) if (l.pattern.equals(p)) return true;
        return false;
    }

    public int layerCount() {
        return (pattern.equals("none") ? 0 : 1) + layers.size();
    }

    public static TrimSettings deserialize(String raw) {
        TrimSettings s = new TrimSettings();
        s.style = defaultStyle;
        if (raw == null) return s;
        Color c1 = null;
        Color c2 = null;
        for (String part : raw.split(";")) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String v = kv[1];
            try {
                switch (kv[0]) {
                    case "name" -> s.name = cleanName(v);
                    case "pattern" -> s.pattern = v.toLowerCase(Locale.ROOT);
                    case "material" -> s.material = v.toLowerCase(Locale.ROOT);
                    case "colors" -> {
                        List<Color> list = new ArrayList<>();
                        for (String h : v.split(",")) {
                            if (!h.isBlank()) list.add(Color.fromRGB(Integer.parseInt(h.trim(), 16) & 0xFFFFFF));
                        }
                        s.setColors(list);
                    }
                    // older format (v1) stored two colors
                    case "c1" -> c1 = Color.fromRGB(Integer.parseInt(v, 16) & 0xFFFFFF);
                    case "c2" -> c2 = Color.fromRGB(Integer.parseInt(v, 16) & 0xFFFFFF);
                    case "trail" -> s.trailParticle = Enums.parse(ParticleStyle.class, v, s.trailParticle);
                    case "trailShape" -> s.trailShape = Enums.parse(TrailShape.class, v, s.trailShape);
                    case "aura" -> s.auraShape = Enums.parse(AuraShape.class, v, s.auraShape);
                    case "auraParticle" -> s.auraParticle = Enums.parse(ParticleStyle.class, v, s.auraParticle);
                    case "size" -> s.size = Double.parseDouble(v);
                    case "density" -> s.density = Double.parseDouble(v);
                    case "glint" -> s.glint = Boolean.parseBoolean(v);
                    case "style" -> s.style = Enums.parse(EffectStyle.class, v, s.style);
                    case "layers" -> {
                        s.layers = new ArrayList<>();
                        for (String part2 : v.split(",")) {
                            Layer l = Layer.parse(part2);
                            if (l != null) s.layers.add(l);
                        }
                    }
                    case "speed" -> s.speed = Double.parseDouble(v);
                    case "height" -> s.height = Double.parseDouble(v);
                    case "cycle" -> s.cycle = v.toLowerCase(Locale.ROOT);
                    case "cycleSeconds" -> s.cycleSeconds = Double.parseDouble(v);
                    case "cycleIndex" -> s.cycleIndex = Integer.parseInt(v);
                    case "cycleAura" -> s.cycleAura = v.toLowerCase(Locale.ROOT);
                    default -> {
                    }
                }
            } catch (IllegalArgumentException ignored) {
                // bad value in stored data: keep the default for that field
            }
        }
        if (c1 != null) s.setColors(c2 != null ? List.of(c1, c2) : List.of(c1));
        return s;
    }
}
