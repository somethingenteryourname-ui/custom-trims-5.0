package dev.customtrims.effect;

import org.bukkit.Particle;

/**
 * Every particle look a trail or aura can use.
 * The first group uses your trim colors, the rest are vanilla particles.
 */
public enum ParticleStyle {
    NONE(null, false),

    // ---- Colored (use your trim colors, 1 to 8 of them) ----
    DUST(null, true),        // solid first color
    DUAL(null, true),        // steps through each of your colors
    GRADIENT(null, true),    // particles fade from one color into the next
    BLEND(null, true),       // smoothly blends through all your colors along the shape
    RAINBOW(null, false),    // cycling rainbow
    SPARKLE(null, true),     // your colors with glittering end-rod sparks

    // ---- Vanilla particles ----
    FLAME(Particle.FLAME),
    SOUL_FLAME(Particle.SOUL_FIRE_FLAME),
    END_ROD(Particle.END_ROD),
    HEART(Particle.HEART),
    ENCHANT(Particle.ENCHANT),
    WITCH(Particle.WITCH),
    ELECTRIC(Particle.ELECTRIC_SPARK),
    SNOW(Particle.SNOWFLAKE),
    TOTEM(Particle.TOTEM_OF_UNDYING),
    PORTAL(Particle.PORTAL),
    REVERSE_PORTAL(Particle.REVERSE_PORTAL),
    NOTE(Particle.NOTE),
    SCULK(Particle.SCULK_SOUL),
    SOUL(Particle.SOUL),
    GLOW(Particle.GLOW),
    FIREWORK(Particle.FIREWORK),
    CLOUD(Particle.CLOUD),
    CRIT(Particle.CRIT),
    MAGIC_CRIT(Particle.ENCHANTED_HIT),
    HAPPY(Particle.HAPPY_VILLAGER),
    ASH(Particle.ASH),
    WHITE_ASH(Particle.WHITE_ASH),
    CRIMSON(Particle.CRIMSON_SPORE),
    WARPED(Particle.WARPED_SPORE),
    NAUTILUS(Particle.NAUTILUS),
    DOLPHIN(Particle.DOLPHIN),
    SMOKE(Particle.SMOKE),
    WAX(Particle.WAX_ON),
    SCRAPE(Particle.SCRAPE),
    CHERRY(Particle.CHERRY_LEAVES),
    OBSIDIAN_TEAR(Particle.FALLING_OBSIDIAN_TEAR),
    LAVA(Particle.LAVA),
    FIREFLY(Particle.FIREFLY),
    COPPER_FLAME(Particle.COPPER_FIRE_FLAME),
    SMALL_FLAME(Particle.SMALL_FLAME),
    PALE_LEAVES(Particle.PALE_OAK_LEAVES),
    TINTED_LEAVES(Particle.TINTED_LEAVES),   // leaves in your colors
    SWIRL(Particle.ENTITY_EFFECT),           // potion swirls in your colors
    SPORE(Particle.FALLING_SPORE_BLOSSOM),
    SPORE_AIR(Particle.SPORE_BLOSSOM_AIR),
    SCULK_POP(Particle.SCULK_CHARGE_POP),
    TRIAL_OMEN(Particle.TRIAL_OMEN),
    RAID_OMEN(Particle.RAID_OMEN),
    OMINOUS(Particle.OMINOUS_SPAWNING),
    DUST_PLUME(Particle.DUST_PLUME),
    INK(Particle.SQUID_INK),
    GLOW_INK(Particle.GLOW_SQUID_INK),
    BUBBLE_POP(Particle.BUBBLE_POP),
    HONEY(Particle.FALLING_HONEY),
    NECTAR(Particle.FALLING_NECTAR),
    DRIP_LAVA(Particle.DRIPPING_DRIPSTONE_LAVA),
    FALLING_LAVA(Particle.FALLING_LAVA),
    SNEEZE(Particle.SNEEZE),
    COMPOSTER(Particle.COMPOSTER),
    WHITE_SMOKE(Particle.WHITE_SMOKE),
    WAX_OFF(Particle.WAX_OFF),
    POOF(Particle.POOF),
    SMALL_GUST(Particle.SMALL_GUST),
    MYCELIUM(Particle.MYCELIUM),
    SPLASH(Particle.SPLASH),
    ANGRY(Particle.ANGRY_VILLAGER);

    private final Particle particle;
    private final boolean usesColor;

    ParticleStyle(Particle particle) {
        this(particle, false);
    }

    ParticleStyle(Particle particle, boolean usesColor) {
        this.particle = particle;
        this.usesColor = usesColor;
    }

    public Particle particle() {
        return particle;
    }

    public boolean usesColor() {
        return usesColor;
    }
}
