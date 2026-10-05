package dev.customtrims.effect;

import dev.customtrims.trim.TrimSettings;
import dev.customtrims.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

import java.util.concurrent.ThreadLocalRandom;

/** Spawns one "point" of a trail or aura in the chosen style. */
public final class ParticleRenderer {

    /** When set, particles are only sent to these players (used to hide your own aura from yourself). */
    public static java.util.List<org.bukkit.entity.Player> viewers;

    private ParticleRenderer() {
    }

    private static <T> void spawn(World w, Particle p, Location loc, int count, double sx, double sy, double sz, double extra, T data) {
        if (viewers == null) {
            if (data == null) w.spawnParticle(p, loc, count, sx, sy, sz, extra);
            else w.spawnParticle(p, loc, count, sx, sy, sz, extra, data);
            return;
        }
        for (org.bukkit.entity.Player v : viewers) {
            if (data == null) v.spawnParticle(p, loc, count, sx, sy, sz, extra);
            else v.spawnParticle(p, loc, count, sx, sy, sz, extra, data);
        }
    }

    /**
     * @param index  position of this point in its shape (used by DUAL to step through colors)
     * @param hue    0..1 position used by BLEND, GRADIENT and RAINBOW
     * @param count  how many particles to spawn at this point
     * @param spread random offset radius
     * @param size   dust size for colored styles
     */
    public static void draw(Location loc, ParticleStyle style, TrimSettings s,
                            int index, double hue, int count, double spread, float size) {
        World w = loc.getWorld();
        if (w == null || style == null || style == ParticleStyle.NONE) return;
        size = Math.max(0.2f, Math.min(3.0f, size));

        switch (style) {
            case DUST -> dust(w, loc, s.primary(), count, spread, size);
            case DUAL -> dust(w, loc, s.cycle(index), count, spread, size);
            case GRADIENT -> {
                // each particle fades from one of your colors into the next one
                Color from = s.sample(triangle(hue));
                Color to = s.sample(triangle(hue + 0.3));
                spawn(w, Particle.DUST_COLOR_TRANSITION, loc, count, spread, spread, spread, 0,
                        new Particle.DustTransition(from, to, size));
            }
            case BLEND -> dust(w, loc, s.sample(triangle(hue)), count, spread, size);
            case RAINBOW -> dust(w, loc, ColorUtil.hue(hue), count, spread, size);
            case SPARKLE -> {
                dust(w, loc, s.cycle(index), count, spread, size);
                if (ThreadLocalRandom.current().nextInt(5) == 0) {
                    spawn(w, Particle.END_ROD, loc, 1, spread, spread, spread, 0, null);
                }
            }
            default -> vanilla(w, style.particle(), loc, count, spread, s.cycle(index));
        }
    }

    private static double triangle(double h) {
        h = h - Math.floor(h);
        return h < 0.5 ? h * 2 : (1 - h) * 2;
    }

    private static void dust(World w, Location loc, Color c, int count, double spread, float size) {
        spawn(w, Particle.DUST, loc, count, spread, spread, spread, 0, new Particle.DustOptions(c, size));
    }

    /** Spawns a vanilla particle, supplying whatever extra data it needs. */
    private static void vanilla(World w, Particle p, Location loc, int count, double spread, Color color) {
        if (p == null) return;
        Class<?> type = p.getDataType();
        if (type == Void.class) {
            spawn(w, p, loc, count, spread, spread, spread, 0, null);
        } else if (type == Particle.DustOptions.class) {
            spawn(w, p, loc, count, spread, spread, spread, 0, new Particle.DustOptions(color, 1f));
        } else if (type == Color.class) {
            spawn(w, p, loc, count, spread, spread, spread, 0, color);
        } else if (type == Float.class) {
            spawn(w, p, loc, count, spread, spread, spread, 0, 1.0f);
        } else if (type == Integer.class) {
            spawn(w, p, loc, count, spread, spread, spread, 0, 0);
        }
        // any other data type: skip quietly instead of throwing every tick
    }
}
