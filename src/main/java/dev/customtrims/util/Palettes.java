package dev.customtrims.util;

import org.bukkit.Color;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** The 8-color trim palettes: vanilla ones, and ones built from custom material colors. */
public final class Palettes {

    private static final Map<String, int[]> VANILLA = new HashMap<>();

    static {
        put("amethyst", 0xC98FF3, 0x9A5CC6, 0x6C49AA, 0x523687, 0x422776, 0x361C6A, 0x240C53, 0x17063B);
        put("copper", 0xE3826C, 0xB4684D, 0x9A472C, 0x793C28, 0x6D3420, 0x5F2B18, 0x4C2010, 0x3D180B);
        put("diamond", 0xCBFFF5, 0x6EECD2, 0x2CBAA8, 0x1D969A, 0x0C788D, 0x076578, 0x04515F, 0x013C47);
        put("emerald", 0x82F6AD, 0x0EC754, 0x11A036, 0x107B24, 0x0E7222, 0x09631B, 0x035013, 0x023D0E);
        put("gold", 0xFFFD90, 0xECD93F, 0xDEB12D, 0xB16712, 0xA0450A, 0x803503, 0x712D00, 0x572300);
        put("iron", 0xC5D2D4, 0xBFC9C8, 0x9DAAAA, 0x7B8989, 0x717D7D, 0x657070, 0x576363, 0x465151);
        put("lapis", 0x416E97, 0x1C4D9C, 0x21497B, 0x123365, 0x112E63, 0x0C285A, 0x091E45, 0x051636);
        put("netherite", 0x5A575A, 0x443A3B, 0x312E31, 0x2F2727, 0x231E1E, 0x1A1616, 0x100C0C, 0x090707);
        put("quartz", 0xF2EFED, 0xF6EADF, 0xE3DBC4, 0xB6AD96, 0x908E80, 0x656156, 0x45433C, 0x2A2822);
        put("redstone", 0xE62008, 0xBD2008, 0x971607, 0x781101, 0x650B01, 0x520D06, 0x360803, 0x1D0502);
        put("resin", 0xFFC354, 0xF69F3B, 0xF0852A, 0xEC7214, 0xDB5F10, 0xC7490A, 0xA53B11, 0x802A1A);
    }

    /** Gray values the trim textures use, bright to dark (vanilla trim_palette key). */
    public static final int[] KEY = {224, 192, 160, 128, 96, 64, 32, 0};

    private Palettes() {
    }

    private static void put(String name, int... rgb) {
        VANILLA.put(name, rgb);
    }

    public static int[] vanilla(String material) {
        return VANILLA.get(material);
    }

    /**
     * Builds 8 palette slots from custom colors. The new trim textures shade from slot 0 (top
     * of the body) to slot 4 (feet), so several colors blend down the armor. Slots 5-7 are shadows.
     */
    public static int[] fromColors(List<Color> colors) {
        Color[] slots = new Color[8];
        if (colors.size() == 1) {
            Color c = colors.get(0);
            slots[0] = ColorUtil.lerp(c, Color.WHITE, 0.35);
            double[] f = {1.0, 0.86, 0.74, 0.62, 0.5, 0.4, 0.3};
            for (int i = 1; i < 8; i++) slots[i] = ColorUtil.darken(c, f[i - 1]);
        } else {
            for (int i = 0; i < 5; i++) slots[i] = ColorUtil.sample(colors, i / 4.0);
            for (int i = 5; i < 8; i++) slots[i] = ColorUtil.darken(slots[i - 3], 0.55);
        }
        int[] out = new int[8];
        for (int i = 0; i < 8; i++) out[i] = slots[i].asRGB();
        return out;
    }

    /** Two nice particle colors that match a vanilla material. */
    public static List<Color> particleColors(String vanillaMaterial) {
        int[] p = VANILLA.get(vanillaMaterial);
        if (p == null) return List.of(Color.WHITE);
        return List.of(Color.fromRGB(p[0]), Color.fromRGB(p[3]));
    }
}
