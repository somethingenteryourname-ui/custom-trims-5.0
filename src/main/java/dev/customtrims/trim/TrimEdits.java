package dev.customtrims.trim;

import dev.customtrims.effect.AuraShape;
import dev.customtrims.effect.EffectStyle;
import dev.customtrims.effect.ParticleStyle;
import dev.customtrims.effect.TrailShape;
import dev.customtrims.util.ColorUtil;
import org.bukkit.Color;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Every way a trim can be changed, shared by the /ctrim commands and the Prism Template in the
 * smithing table (so the same words work in both places).
 */
public final class TrimEdits {

    /** Edit names, in the order shown in help and tab completion. */
    public static final List<String> EDITS = List.of(
            "color", "trail", "trailshape", "aura", "auraparticle", "pattern", "material", "layer",
            "style", "size", "density", "speed", "height", "glint", "name", "cycle", "cyclespeed", "cycleaura");

    private TrimEdits() {
    }

    public static String usage(TrimManager tm, String sub, String l) {
        return switch (sub) {
            case "color", "colour", "colors" -> "/" + l + " color <color1> [color2] ... (up to 8)  |  color add <color>  |  color reset";
            case "trail" -> "/" + l + " trail <particle> [shape]";
            case "trailshape" -> "/" + l + " trailshape <shape>";
            case "aura" -> "/" + l + " aura <shape> [particle]";
            case "auraparticle" -> "/" + l + " auraparticle <particle>";
            case "pattern" -> "/" + l + " pattern <pattern> [material]";
            case "material" -> "/" + l + " material <material>";
            case "layer" -> "/" + l + " layer add <new pattern> <material>  |  layer remove <number>  |  layer clear";
            case "style" -> "/" + l + " style <liquid|particles|both>";
            case "size" -> "/" + l + " size <0.3-" + tm.getMaxSize() + ">";
            case "density" -> "/" + l + " density <0.25-" + tm.getMaxDensity() + ">";
            case "speed" -> "/" + l + " speed <0.1-5>";
            case "height" -> "/" + l + " height <-1.5 to 1.5>";
            case "glint" -> "/" + l + " glint <on|off|toggle>";
            case "name" -> "/" + l + " name <text>";
            case "cycle" -> "/" + l + " cycle <off|presets|random|patterns|colors|auras|trails>";
            case "cyclespeed" -> "/" + l + " cyclespeed <seconds between changes, 0.5-600>";
            case "cycleaura" -> "/" + l + " cycleaura <on|keep|off>  (on = aura changes too, keep = keep yours, off = no aura)";
            default -> "/" + l + " help";
        };
    }

    /** Runs a text action like "aura wings" or "trail flame comet". Returns an error, or null if it worked. */
    public static String action(TrimManager tm, TrimSettings s, String action) {
        String[] words = action.trim().split("\\s+");
        if (words.length == 0 || words[0].isEmpty()) return "Empty action.";
        String sub = words[0].toLowerCase(Locale.ROOT);
        if (words.length < 2 && !sub.equals("glint")) return "Missing value for " + sub + ".";
        if (words.length < 2) words = new String[]{"glint", "toggle"};
        return apply(tm, s, sub, words);
    }

    /** a[0] is the edit name, a[1..] are its values. Returns an error message, or null on success. */
    public static String apply(TrimManager tm, TrimSettings s, String sub, String[] a) {
        switch (sub) {
            case "color", "colour", "colors" -> {
                String first = a[1].toLowerCase(Locale.ROOT);
                if (first.equals("reset")) {
                    s.setColors(List.of(s.primary()));
                    return null;
                }
                if (first.equals("add")) {
                    if (a.length < 3) return "Say which color to add.";
                    Color c = ColorUtil.parse(a[2]);
                    if (c == null) return "Unknown color '" + a[2] + "'.";
                    addColor(s, c);
                    return null;
                }
                List<Color> list = new ArrayList<>();
                for (int i = 1; i < a.length && list.size() < TrimSettings.MAX_COLORS; i++) {
                    Color c = ColorUtil.parse(a[i]);
                    if (c == null) return "Unknown color '" + a[i] + "'. Use a name (see /ctrim options) or hex like #FF00AA.";
                    list.add(c);
                }
                s.setColors(list);
            }
            case "trail" -> {
                ParticleStyle st = dev.customtrims.util.Enums.parse(ParticleStyle.class, a[1], null);
                if (st == null) return "Unknown particle. See /ctrim options";
                s.trailParticle = st;
                if (a.length >= 3) {
                    TrailShape shape = dev.customtrims.util.Enums.parse(TrailShape.class, a[2], null);
                    if (shape == null) return "Unknown trail shape. See /ctrim options";
                    s.trailShape = shape;
                }
            }
            case "trailshape", "trail-shape" -> {
                TrailShape shape = dev.customtrims.util.Enums.parse(TrailShape.class, a[1], null);
                if (shape == null) return "Unknown trail shape. See /ctrim options";
                s.trailShape = shape;
                if (s.trailParticle == ParticleStyle.NONE) s.trailParticle = ParticleStyle.BLEND;
            }
            case "aura" -> {
                AuraShape shape = dev.customtrims.util.Enums.parse(AuraShape.class, a[1], null);
                if (shape == null) return "Unknown aura shape. See /ctrim options";
                s.auraShape = shape;
                if (a.length >= 3) {
                    ParticleStyle st = dev.customtrims.util.Enums.parse(ParticleStyle.class, a[2], null);
                    if (st == null) return "Unknown particle. See /ctrim options";
                    s.auraParticle = st;
                } else if (s.auraParticle == ParticleStyle.NONE && shape != AuraShape.NONE) {
                    s.auraParticle = ParticleStyle.BLEND;
                }
            }
            case "auraparticle", "aura-particle" -> {
                ParticleStyle st = dev.customtrims.util.Enums.parse(ParticleStyle.class, a[1], null);
                if (st == null) return "Unknown particle. See /ctrim options";
                s.auraParticle = st;
            }
            case "pattern" -> {
                String pat = a[1].toLowerCase(Locale.ROOT);
                if (!tm.isKnownPattern(pat)) return "Unknown pattern. See /ctrim options";
                s.pattern = pat;
                s.layers.removeIf(l -> l.pattern.equals(pat));
                if (a.length >= 3) {
                    String mat = a[2].toLowerCase(Locale.ROOT);
                    if (!tm.isKnownMaterial(mat)) return "Unknown material. See /ctrim options";
                    s.material = mat;
                }
            }
            case "material" -> {
                String mat = a[1].toLowerCase(Locale.ROOT);
                if (!tm.isKnownMaterial(mat)) return "Unknown material. See /ctrim options";
                s.material = mat;
            }
            case "layer", "layers" -> {
                String op = a[1].toLowerCase(Locale.ROOT);
                switch (op) {
                    case "add" -> {
                        if (a.length < 4) return "Use: layer add <new pattern> <material>";
                        String pat = a[2].toLowerCase(Locale.ROOT);
                        String mat = a[3].toLowerCase(Locale.ROOT);
                        if (!TrimManager.isCustomPattern(pat)) return "Extra layers must be one of the NEW patterns (see /ctrim options).";
                        if (!tm.isKnownMaterial(mat)) return "Unknown material. See /ctrim options";
                        if (s.layerCount() >= tm.getMaxLayers() && !s.hasPattern(pat)) {
                            return "This armor already has " + tm.getMaxLayers() + " trims. Remove one first (layer remove <number>).";
                        }
                        tm.addTrim(s, pat, mat);
                    }
                    case "remove" -> {
                        if (a.length < 3) return "Use: layer remove <number> (see /ctrim info)";
                        int n;
                        try {
                            n = Integer.parseInt(a[2]);
                        } catch (NumberFormatException e) {
                            return "Use a layer number, like 2.";
                        }
                        // layer 1 is the main trim, 2+ are the extra layers
                        if (n == 1) {
                            if (s.layers.isEmpty()) s.pattern = "none";
                            else {
                                Layer next = s.layers.remove(0);
                                s.pattern = next.pattern;
                                s.material = next.material;
                            }
                        } else if (n >= 2 && n - 2 < s.layers.size()) {
                            s.layers.remove(n - 2);
                        } else {
                            return "There's no layer " + n + ".";
                        }
                    }
                    case "clear" -> s.layers.clear();
                    default -> {
                        return "Use: layer add | layer remove | layer clear";
                    }
                }
            }
            case "style" -> {
                EffectStyle st = dev.customtrims.util.Enums.parse(EffectStyle.class, a[1], null);
                if (st == null) return "Use liquid, particles or both.";
                s.style = st;
            }
            case "size" -> {
                Double v = number(a[1]);
                if (v == null) return "Size must be a number, like 1.5";
                s.size = tm.clampSize(v);
            }
            case "density" -> {
                Double v = number(a[1]);
                if (v == null) return "Density must be a number, like 1.5";
                s.density = tm.clampDensity(v);
            }
            case "speed" -> {
                Double v = number(a[1]);
                if (v == null) return "Speed must be a number, like 1.5";
                s.speed = Math.max(0.1, Math.min(5, v));
            }
            case "height" -> {
                Double v = number(a[1]);
                if (v == null) return "Height must be a number, like 0.5 or -0.3";
                s.height = Math.max(-1.5, Math.min(1.5, v));
            }
            case "glint" -> {
                String v = a[1].toLowerCase(Locale.ROOT);
                if (v.equals("on") || v.equals("true") || v.equals("yes")) s.glint = true;
                else if (v.equals("off") || v.equals("false") || v.equals("no")) s.glint = false;
                else if (v.equals("toggle")) s.glint = !s.glint;
                else return "Use on, off or toggle.";
            }
            case "name" -> s.name = TrimSettings.cleanName(String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            case "cycle" -> {
                String mode = a[1].toLowerCase(Locale.ROOT);
                if (mode.equals("on") || mode.equals("all")) mode = "random";
                if (!dev.customtrims.effect.CycleTask.MODES.contains(mode)) {
                    return "Use: " + String.join(", ", dev.customtrims.effect.CycleTask.MODES);
                }
                s.cycle = mode;
            }
            case "cycleaura", "cycle-aura" -> {
                String v = a[1].toLowerCase(Locale.ROOT);
                if (!v.equals("on") && !v.equals("keep") && !v.equals("off")) return "Use on, keep or off.";
                s.cycleAura = v;
                if (v.equals("off") && !s.cycle.equals("off")) s.auraShape = AuraShape.NONE;
            }
            case "cyclespeed", "cycle-speed" -> {
                Double v = number(a[1]);
                if (v == null) return "Use the seconds between changes, like 3 or 0.5";
                s.cycleSeconds = Math.max(0.5, Math.min(600, v));
            }
            default -> {
                return "Unknown option.";
            }
        }
        return null;
    }

    /** Adds a color to the blend (the oldest one drops off when there are already 8). */
    public static void addColor(TrimSettings s, Color c) {
        List<Color> list = new ArrayList<>(s.colors);
        if (list.size() >= TrimSettings.MAX_COLORS) list.remove(0);
        list.add(c);
        s.setColors(list);
    }

    private static Double number(String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
