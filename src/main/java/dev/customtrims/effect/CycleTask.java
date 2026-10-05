package dev.customtrims.effect;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.trim.TrimSettings;
import dev.customtrims.util.ColorUtil;
import org.bukkit.Color;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** Cycle mode: keeps switching the trim on worn armor (players and armor stands) forever. */
public final class CycleTask extends BukkitRunnable {

    public static final List<String> MODES = List.of("off", "presets", "random", "patterns", "colors", "auras", "trails");

    private final CustomTrimsPlugin plugin;
    private final Map<UUID, Long> lastSwitch = new HashMap<>();

    public CycleTask(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        TrimManager tm = plugin.getTrimManager();
        for (LivingEntity e : plugin.getWearers().all()) {
            UUID id = e.getUniqueId();
            TrimSettings s = tm.getActive(e);
            if (s == null || s.cycle == null || s.cycle.equals("off")) {
                lastSwitch.remove(id);
                continue;
            }
            Long last = lastSwitch.get(id);
            if (last == null) {
                lastSwitch.put(id, now);
                continue;
            }
            if (now - last < Math.max(0.5, s.cycleSeconds) * 1000) continue;
            lastSwitch.put(id, now);
            tm.applyToEquipment(e, next(tm, s));
            plugin.invalidate(e);
        }
        if (lastSwitch.size() > 500) lastSwitch.clear();
    }

    private TrimSettings next(TrimManager tm, TrimSettings s) {
        int idx = s.cycleIndex + 1;
        TrimSettings n;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        switch (s.cycle) {
            case "presets" -> {
                List<TrimSettings> list = new ArrayList<>(tm.getPresets().values());
                if (list.isEmpty()) return s;
                n = list.get(Math.floorMod(idx, list.size())).copy();
                n.style = s.style;
            }
            case "patterns" -> {
                n = s.copy();
                List<String> pats = tm.allPatterns();
                n.pattern = pats.get(Math.floorMod(idx, pats.size()));
                n.layers.removeIf(l -> l.pattern.equals(n.pattern));
            }
            case "colors" -> {
                n = s.copy();
                List<String> mats = tm.allMaterials();
                n.material = mats.get(Math.floorMod(idx, mats.size()));
                n.setColors(tm.colorsFor(n.material));
            }
            case "auras" -> {
                n = s.copy();
                AuraShape[] shapes = AuraShape.values();
                n.auraShape = shapes[1 + Math.floorMod(idx, shapes.length - 1)];   // skip NONE
                if (n.auraParticle == ParticleStyle.NONE) n.auraParticle = ParticleStyle.BLEND;
            }
            case "trails" -> {
                n = s.copy();
                ParticleStyle[] styles = ParticleStyle.values();
                TrailShape[] shapes = TrailShape.values();
                n.trailParticle = styles[1 + Math.floorMod(idx, styles.length - 1)];
                n.trailShape = shapes[Math.floorMod(idx, shapes.length)];
            }
            default -> {   // random: everything changes
                n = s.copy();
                List<String> pats = tm.allPatterns();
                List<String> mats = tm.allMaterials();
                n.pattern = pats.get(rnd.nextInt(pats.size()));
                n.material = mats.get(rnd.nextInt(mats.size()));
                n.layers.clear();
                List<Color> colors = new ArrayList<>();
                double h = rnd.nextDouble();
                int count = 1 + rnd.nextInt(4);
                for (int i = 0; i < count; i++) colors.add(ColorUtil.hue(h + i * rnd.nextDouble(0.08, 0.3)));
                n.setColors(colors);
                AuraShape[] shapes = AuraShape.values();
                n.auraShape = shapes[1 + rnd.nextInt(shapes.length - 1)];
                ParticleStyle[] colored = {ParticleStyle.BLEND, ParticleStyle.GRADIENT, ParticleStyle.DUAL, ParticleStyle.SPARKLE, ParticleStyle.RAINBOW};
                n.auraParticle = colored[rnd.nextInt(colored.length)];
                ParticleStyle[] styles = ParticleStyle.values();
                n.trailParticle = styles[1 + rnd.nextInt(styles.length - 1)];
                TrailShape[] trails = TrailShape.values();
                n.trailShape = trails[rnd.nextInt(trails.length)];
                n.name = "Random";
            }
        }
        n.cycle = s.cycle;
        n.cycleSeconds = s.cycleSeconds;
        n.cycleIndex = idx;
        n.cycleAura = s.cycleAura;
        if ("off".equals(s.cycleAura)) {
            n.auraShape = AuraShape.NONE;
        } else if ("keep".equals(s.cycleAura)) {
            n.auraShape = s.auraShape;
            n.auraParticle = s.auraParticle;
            n.size = s.size;
            n.height = s.height;
        }
        return n;
    }
}
