package dev.customtrims.effect;

import dev.customtrims.CustomTrimsPlugin;
import dev.customtrims.trim.TrimManager;
import dev.customtrims.trim.TrimSettings;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import dev.customtrims.util.ColorBlend;

/**
 * Draws trails and auras as flowing liquid using item display entities with the
 * animated liquid models from the CustomTrims resource pack. Runs every tick.
 */
public final class LiquidTask extends BukkitRunnable {

    public static final String TAG = "customtrims_fx";
    private static final int MAX_PIECES = 600;
    private static final int RING_TINTS = 32;

    private final CustomTrimsPlugin plugin;
    private static final int PUBLIC_TELEPORT_TICKS = 3;   // matches how smoothly other players see you move

    private final boolean glow;
    private final int blendTicks;
    private final ColorBlend blend;
    private final List<Aura> fading = new ArrayList<>();
    private final Map<Aura, UUID> fadingOwner = new HashMap<>();
    private final boolean onlyWhenMoving;
    private final boolean hideInvisible;
    private final boolean hideSneaking;
    private final int colorFlowTicks;

    private long tick;
    private final Map<UUID, Aura> auras = new HashMap<>();
    private final Map<UUID, TrimSettings> cache = new HashMap<>();
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Integer> footsteps = new HashMap<>();
    private final List<Piece> pieces = new ArrayList<>();

    public LiquidTask(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
        this.glow = plugin.getConfig().getBoolean("liquid.glow", true);
        this.blendTicks = plugin.blendTicks();
        this.blend = new ColorBlend(blendTicks);
        this.onlyWhenMoving = plugin.getConfig().getBoolean("trail-only-when-moving", true);
        this.hideInvisible = plugin.getConfig().getBoolean("hide-when-invisible", true);
        this.hideSneaking = plugin.getConfig().getBoolean("hide-when-sneaking", false);
        this.colorFlowTicks = Math.max(1, plugin.getConfig().getInt("liquid.color-flow-ticks", 3));
    }

    public void invalidate(UUID id) {
        cache.remove(id);
    }

    /** Removes every liquid entity this plugin made, including leftovers from a crash or /reload. */
    public static void removeAllTagged() {
        for (World w : Bukkit.getWorlds()) {
            for (ItemDisplay d : w.getEntitiesByClass(ItemDisplay.class)) {
                if (d.getScoreboardTags().contains(TAG)) d.remove();
            }
        }
    }

    public void shutdown() {
        cancel();
        for (Aura a : auras.values()) a.remove();
        auras.clear();
        for (Aura a : fading) a.remove();
        fading.clear();
        fadingOwner.clear();
        for (Piece p : pieces) p.entity.remove();
        pieces.clear();
    }

    // ===================================================================== tick

    @Override
    public void run() {
        tick++;
        boolean refresh = tick % 10 == 0;

        for (LivingEntity p : plugin.getWearers().all()) {
            UUID id = p.getUniqueId();
            if (refresh || !cache.containsKey(id)) cache.put(id, plugin.getTrimManager().getActive(p));
            TrimSettings s = cache.get(id);

            Location now = p.getLocation();
            Location prev = lastLocation.put(id, now);

            boolean wantLiquid = s != null && s.style != EffectStyle.PARTICLES && visible(p);
            if (!wantLiquid) {
                fadeOut(auras.remove(id), id);
                continue;
            }

            // colors fade smoothly into new ones (cycle mode, recoloring...)
            TrimSettings view = s.copy();
            view.setColors(blend.colors(id, s.colors, tick));
            boolean blending = blend.isBlending(id, tick);

            // the wearer's own copy is placed a little ahead of them so it never trails behind
            Vector lead = new Vector(0, 0, 0);
            if (p instanceof Player pl && prev != null && prev.getWorld() == now.getWorld()) {
                double ticksAhead = Math.min(8, 1 + pl.getPing() / 50.0);
                lead = now.toVector().subtract(prev.toVector()).multiply(ticksAhead);
                if (lead.lengthSquared() > 4) lead = new Vector(0, 0, 0);   // teleported: don't fling it
            }

            // ---- aura
            if (s.auraShape == AuraShape.NONE || s.auraParticle == ParticleStyle.NONE) {
                fadeOut(auras.remove(id), id);
            } else {
                boolean selfView = p instanceof Player pl && plugin.seesOwnAura(pl);
                Aura aura = auras.get(id);
                String key = auraKey(s, now.getWorld(), selfView);
                if (aura == null || !aura.key.equals(key) || !aura.valid()) {
                    if (aura != null && !aura.valid()) aura.remove();
                    else fadeOut(aura, id);
                    aura = new Aura(key, p, view, selfView);
                    auras.put(id, aura);
                }
                aura.update(p, now, view, lead, blending);
            }
            s = view;

            // ---- trail
            if (s.trailParticle != ParticleStyle.NONE && tick % 2 == 0) {
                boolean moving = prev != null && prev.getWorld() == now.getWorld() && prev.distanceSquared(now) > 0.0025;
                if (moving || !onlyWhenMoving) spawnTrail(p, s, now, prev, moving);
            }
        }

        // ---- old auras shrinking away
        Iterator<Aura> fit = fading.iterator();
        while (fit.hasNext()) {
            Aura a = fit.next();
            org.bukkit.entity.Entity owner = Bukkit.getEntity(fadingOwner.get(a));
            if (tick >= a.dieTick || !(owner instanceof LivingEntity le) || !le.isValid()) {
                a.remove();
                fadingOwner.remove(a);
                fit.remove();
                continue;
            }
            a.follow(le, le.getLocation(), new Vector(0, 0, 0));
        }

        // ---- trail pieces: start their animation, then remove them when done
        Iterator<Piece> it = pieces.iterator();
        while (it.hasNext()) {
            Piece piece = it.next();
            piece.age++;
            if (!piece.entity.isValid()) {
                it.remove();
                continue;
            }
            if (piece.age == 1) {
                animate(piece.entity, piece.end, piece.endTicks);
                if (piece.moveTo != null) {
                    piece.entity.setTeleportDuration(Math.min(59, piece.endTicks));
                    piece.entity.teleport(piece.moveTo);
                }
            }
            if (piece.end2 != null && piece.age == piece.end2At) animate(piece.entity, piece.end2, piece.end2Ticks);
            if (piece.age > piece.life) {
                piece.entity.remove();
                it.remove();
            }
        }

        if (tick % 200 == 0) {
            auras.entrySet().removeIf(e -> {
                if (Bukkit.getEntity(e.getKey()) != null) return false;
                e.getValue().remove();
                return true;
            });
            cache.keySet().removeIf(u -> Bukkit.getEntity(u) == null);
            lastLocation.keySet().removeIf(u -> Bukkit.getEntity(u) == null);
            footsteps.keySet().removeIf(u -> Bukkit.getEntity(u) == null);
            blend.retain(u -> Bukkit.getEntity(u) != null);
        }
    }

    private boolean visible(LivingEntity p) {
        if (p.isDead() || !p.isValid()) return false;
        if (p instanceof Player pl && pl.getGameMode() == GameMode.SPECTATOR) return false;
        if (plugin.isHidden(p.getUniqueId())) return false;
        if (hideInvisible && p.hasPotionEffect(PotionEffectType.INVISIBILITY)) return false;
        return !(hideSneaking && p instanceof Player pl2 && pl2.isSneaking());
    }

    private static String auraKey(TrimSettings s, World w, boolean selfView) {
        return s.auraShape.name() + '|' + s.size + '|' + s.density + '|' + s.height + '|' + w.getUID() + '|' + selfView;
    }

    private void fadeOut(Aura aura, UUID owner) {
        if (aura == null) return;
        aura.fadeOut();
        fading.add(aura);
        fadingOwner.put(aura, owner);
    }

    // ================================================================= helpers

    private static ItemStack liquidItem(String model, List<Color> colors) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setItemModel(new NamespacedKey(TrimManager.NAMESPACE, model));
            CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
            cmd.setColors(colors);
            meta.setCustomModelDataComponent(cmd);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemDisplay spawnDisplay(LivingEntity owner, Location loc, ItemStack item, Transformation start,
                                     Display.Billboard billboard, int teleportTicks) {
        return spawnDisplay(owner, loc, item, start, billboard, teleportTicks, Visibility.ALL);
    }

    private ItemDisplay spawnDisplay(LivingEntity owner, Location loc, ItemStack item, Transformation start,
                                     Display.Billboard billboard, int teleportTicks, Visibility vis) {
        World w = loc.getWorld();
        if (w == null) return null;
        ItemDisplay d = w.spawn(loc, ItemDisplay.class, e -> {
            e.setPersistent(false);
            e.addScoreboardTag(TAG);
            e.setItemStack(item);
            e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
            e.setBillboard(billboard);
            e.setTransformation(start);
            e.setShadowRadius(0f);
            e.setShadowStrength(0f);
            e.setTeleportDuration(teleportTicks);
            e.setViewRange(1.5f);
            if (glow) e.setBrightness(new Display.Brightness(15, 15));
            if (vis == Visibility.ONLY_OWNER) e.setVisibleByDefault(false);
        });
        if (owner instanceof Player op) {
            if (vis == Visibility.NOT_OWNER) op.hideEntity(plugin, d);
            else if (vis == Visibility.ONLY_OWNER) op.showEntity(plugin, d);
        }
        return d;
    }

    private static void animate(ItemDisplay d, Transformation to, int ticks) {
        d.setInterpolationDelay(0);
        d.setInterpolationDuration(ticks);
        d.setTransformation(to);
    }

    private static Transformation tf(float tx, float ty, float tz, Quaternionf rot, float sx, float sy, float sz) {
        return new Transformation(new Vector3f(tx, ty, tz), rot, new Vector3f(sx, sy, sz), new Quaternionf());
    }

    private static Transformation tf(float sx, float sy, float sz) {
        return tf(0, 0, 0, new Quaternionf(), sx, sy, sz);
    }

    private static Vector forward(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.sin(r), 0, Math.cos(r));
    }

    private static Vector right(float yaw) {
        double r = Math.toRadians(yaw);
        return new Vector(-Math.cos(r), 0, -Math.sin(r));
    }

    /** The ring model has 32 color slots around it; this fills them with a smooth loop through the colors. */
    private static List<Color> ringColors(TrimSettings s, double offset, int dir) {
        List<Color> out = new ArrayList<>(RING_TINTS);
        for (int i = 0; i < RING_TINTS; i++) {
            double t = (i / (double) RING_TINTS - dir * offset) % 1.0;
            if (t < 0) t += 1;
            out.add(s.sample(t < 0.5 ? t * 2 : (1 - t) * 2));
        }
        return out;
    }

    private double flowHue() {
        double h = (tick % 120) / 120.0;
        return h < 0.5 ? h * 2 : (1 - h) * 2;   // 0 -> 1 -> 0, so colors flow back and forth smoothly
    }

    // =================================================================== auras

    /** One piece of an aura: a ring, wings or a floating blob. */
    private static final class Part {
        String model;
        double y;          // height above the feet
        float sx = 1, sy = 1, sz = 1;
        Quaternionf tilt = new Quaternionf();
        double spin;       // degrees per tick
        double yawOffset;
        boolean wings;
        boolean pulse;
        boolean firefly;
        double orbitRadius, orbitSpeed, phase;
        boolean headRelative;  // y is measured from the top of the head (follows sneaking)
        int flowDir = 1;       // which way the colors flow around a ring
        int index;
        ItemDisplay entity;    // what everyone else sees
        ItemDisplay self;      // the wearer's own copy (only when they turned self view on)

        void each(java.util.function.Consumer<ItemDisplay> action) {
            if (entity != null) action.accept(entity);
            if (self != null) action.accept(self);
        }

        Transformation full() {
            return tf(0, 0, 0, tilt, sx, sy, sz);
        }
    }

    private enum Visibility { ALL, NOT_OWNER, ONLY_OWNER }

    private final class Aura {
        final String key;
        TrimSettings settings;
        final List<Part> parts = new ArrayList<>();
        int age;
        boolean dying;
        long dieTick;

        Aura(String key, LivingEntity p, TrimSettings s, boolean selfView) {
            this.key = key;
            this.settings = s;
            float size = (float) s.size;
            switch (s.auraShape) {
                case RING -> parts.add(ring(1.0, size, 1f, 0, 0, 0));
                case DOUBLE_RING -> {
                    parts.add(ring(0.35, size, 1f, 0, 0, 0));
                    Part top = ring(-0.35, size, 1f, 0, 0, 0);
                    top.headRelative = true;
                    top.flowDir = -1;
                    parts.add(top);
                }
                case HALO -> {
                    Part r = ring(0.05, 0.42f * size, 0.6f, 0, 0, 0);
                    r.headRelative = true;
                    parts.add(r);
                }
                case CROWN -> {
                    Part r = ring(0.0, 0.45f * size, 1.5f, 0, 0, 0);
                    r.headRelative = true;
                    parts.add(r);
                }
                case HELIX -> parts.add(ring(1.0, size, 1f, 4, 25, 0));
                case DOUBLE_HELIX -> {
                    parts.add(ring(1.0, size, 1f, 4, 30, 0));
                    parts.add(ring(1.0, size, 1f, 4, -30, 90));
                }
                case ORBIT -> {
                    for (int i = 0; i < 3; i++) parts.add(ring(1.0, 0.95f * size, 0.8f, 2.5, 65, i * 60));
                }
                case PULSE -> {
                    Part r = ring(0.1, size, 1f, 2, 0, 0);
                    r.pulse = true;
                    parts.add(r);
                }
                case TORNADO -> {
                    double[] ys = {0.15, 0.7, 1.25, 1.8};
                    float[] sc = {0.35f, 0.6f, 0.85f, 1.1f};
                    for (int i = 0; i < 4; i++) {
                        Part r = ring(ys[i], sc[i] * size, 0.8f, 0, 0, 0);
                        r.flowDir = i % 2 == 0 ? 1 : -1;
                        parts.add(r);
                    }
                }
                case WINGS -> {
                    Part wpart = new Part();
                    wpart.model = "liquid_wings";
                    wpart.wings = true;
                    wpart.y = 0.75;
                    wpart.sx = wpart.sy = wpart.sz = size;
                    parts.add(wpart);
                }
                case SATURN -> {
                    parts.add(ring(1.0, 1.35f * size, 0.75f, 1.5, 22, 0));
                    Part halo = ring(0.1, 0.35f * size, 0.5f, 0, 0, 0);
                    halo.headRelative = true;
                    halo.flowDir = -1;
                    parts.add(halo);
                }
                case SHIELD -> {
                    parts.add(ring(1.0, 1.1f * size, 0.6f, 3, 90, 0));
                    Part second = ring(1.0, 1.1f * size, 0.6f, 3, 90, 90);
                    second.flowDir = -1;
                    parts.add(second);
                }
                case GALAXY -> {
                    float[] sc = {0.45f, 0.8f, 1.15f};
                    for (int i = 0; i < 3; i++) {
                        Part r = ring(1.0 - i * 0.04, sc[i] * size, 0.45f, 0, 0, 0);
                        r.flowDir = i % 2 == 0 ? 1 : -1;
                        parts.add(r);
                    }
                }
                case FIREFLIES -> {
                    ThreadLocalRandom rnd = ThreadLocalRandom.current();
                    int n = Math.max(3, (int) Math.round(5 * s.density));
                    for (int i = 0; i < n; i++) {
                        Part f = new Part();
                        f.model = "liquid_blob";
                        f.firefly = true;
                        f.y = rnd.nextDouble(0.3, 2.0);
                        f.orbitRadius = rnd.nextDouble(0.6, 1.1) * size;
                        f.orbitSpeed = rnd.nextDouble(1.5, 3.5) * (rnd.nextBoolean() ? 1 : -1);
                        f.phase = rnd.nextDouble(360);
                        float bs = (float) rnd.nextDouble(0.35, 0.6);
                        f.sx = f.sy = f.sz = bs;
                        parts.add(f);
                    }
                }
                default -> {
                }
            }

            Location base = p.getLocation();
            boolean isPlayer = p instanceof Player;
            for (int i = 0; i < parts.size(); i++) {
                Part part = parts.get(i);
                part.index = i;
                ItemStack item = liquidItem(part.model, colorsFor(part, s, 0));
                // start tiny and grow in, so a new aura blends in instead of popping up
                Transformation start = tf(0, 0, 0, part.tilt, part.sx * 0.02f, part.sy * 0.02f, part.sz * 0.02f);
                Display.Billboard bb = part.firefly ? Display.Billboard.CENTER : Display.Billboard.FIXED;
                Location at = locationFor(part, base, p);
                part.entity = spawnDisplay(p, at, item, start, bb, PUBLIC_TELEPORT_TICKS,
                        isPlayer ? Visibility.NOT_OWNER : Visibility.ALL);
                if (isPlayer && selfView) {
                    part.self = spawnDisplay(p, at, item.clone(), start, bb, 1, Visibility.ONLY_OWNER);
                }
            }
        }

        private List<Color> colorsFor(Part part, TrimSettings s, double offset) {
            if (part.firefly) return List.of(s.sample(parts.size() <= 1 ? 0 : (double) part.index / (parts.size() - 1)));
            if (part.wings) return List.of(s.sample(0), s.sample(0.5), s.sample(1));
            return ringColors(s, offset, part.flowDir);
        }

        private Part ring(double y, float scale, float height, double spin, float tiltDeg, double yawOffset) {
            Part r = new Part();
            r.model = "liquid_ring";
            r.y = y;
            r.sx = scale;
            r.sz = scale;
            r.sy = height;
            r.spin = spin;
            r.yawOffset = yawOffset;
            if (tiltDeg != 0) r.tilt = new Quaternionf().rotationX((float) Math.toRadians(tiltDeg));
            return r;
        }

        private Location locationFor(Part part, Location base, LivingEntity p) {
            Location l = base.clone();
            double hs = Math.max(0.4, p.getHeight() / 1.8);     // small armor stands get a smaller aura
            double y = (part.headRelative ? p.getEyeHeight() + 0.42 * hs + part.y : part.y * hs) + settings.height;
            double speed = settings.speed;
            if (part.wings) {
                Vector back = forward(base.getYaw()).multiply(-0.32);
                l.add(back).add(0, y, 0);
                l.setYaw(base.getYaw());
            } else if (part.firefly) {
                double a = Math.toRadians(part.phase + tick * part.orbitSpeed * speed);
                double bob = Math.sin(Math.toRadians(part.phase * 2 + tick * 4 * speed)) * 0.15;
                l.add(Math.cos(a) * part.orbitRadius, y + bob, Math.sin(a) * part.orbitRadius);
                l.setYaw(0);
            } else {
                l.add(0, y, 0);
                l.setYaw((float) ((part.yawOffset + tick * part.spin * speed) % 360));
            }
            l.setPitch(0);
            return l;
        }

        /** Moves everything with the wearer. lead = how far ahead to put the wearer's own copy (hides lag). */
        void follow(LivingEntity p, Location base, Vector lead) {
            for (Part part : parts) {
                Location l = locationFor(part, base, p);
                if (part.entity != null) part.entity.teleport(l);
                if (part.self != null) part.self.teleport(l.clone().add(lead));
            }
        }

        void update(LivingEntity p, Location base, TrimSettings view, Vector lead, boolean blending) {
            settings = view;
            age++;
            follow(p, base, lead);
            int flowEvery = Math.max(1, (int) Math.round(colorFlowTicks / Math.max(0.1, settings.speed)));
            boolean flow = (settings.colors.size() > 1 || blending) && tick % flowEvery == 0;
            boolean growing = age <= blendTicks;
            for (Part part : parts) {
                if (age == 1) part.each(d -> animate(d, part.full(), blendTicks));

                // colors flow around the ring like a current (and fade smoothly when they change)
                if (flow && "liquid_ring".equals(part.model)) {
                    double offset = (tick / flowEvery) / (double) RING_TINTS;
                    ItemStack item = liquidItem("liquid_ring", colorsFor(part, settings, offset));
                    part.each(d -> d.setItemStack(item));
                } else if (blending && tick % 4 == 0 && !"liquid_ring".equals(part.model)) {
                    ItemStack item = liquidItem(part.model, colorsFor(part, settings, 0));
                    part.each(d -> d.setItemStack(item));
                }

                if (growing) continue;
                if (part.pulse) {
                    long phase = tick % 26;
                    if (phase == 0) {
                        part.each(d -> animate(d, tf(0, 0, 0, part.tilt, 0.25f * part.sx, 1.2f, 0.25f * part.sz), 0));
                    } else if (phase == 1) {
                        part.each(d -> animate(d, tf(0, 0, 0, part.tilt, 1.9f * part.sx, 0.15f, 1.9f * part.sz), 23));
                    }
                } else if (part.wings && tick % 12 == 0) {
                    // gentle flap: wings spread and fold
                    float spread = (tick / 12) % 2 == 0 ? 1.0f : 0.82f;
                    part.each(d -> animate(d, tf(0, 0, 0, part.tilt, part.sx * spread, part.sy, part.sz), 12));
                }
            }
        }

        /** Shrinks away smoothly, then is removed. */
        void fadeOut() {
            if (dying) return;
            dying = true;
            dieTick = tick + blendTicks + 2;
            for (Part part : parts) {
                part.each(d -> animate(d, tf(0, 0, 0, part.tilt, part.sx * 0.02f, part.sy * 0.02f, part.sz * 0.02f), blendTicks));
            }
        }

        boolean valid() {
            for (Part part : parts) {
                if (part.entity == null || !part.entity.isValid()) return false;
                if (part.self != null && !part.self.isValid()) return false;
            }
            return true;
        }

        void remove() {
            for (Part part : parts) part.each(ItemDisplay::remove);
            parts.clear();
        }
    }

    // ================================================================== trails

    private static final class Piece {
        ItemDisplay entity;
        int age;
        int life;
        Transformation end;
        int endTicks;
        Transformation end2;
        int end2At;
        int end2Ticks;
        Location moveTo;   // optional: glide here (droplets falling / rising)
    }

    private void addPiece(ItemDisplay d, int life, Transformation end, int endTicks) {
        addPiece(d, life, end, endTicks, null);
    }

    private void addPiece(ItemDisplay d, int life, Transformation end, int endTicks, Location moveTo) {
        if (d == null) return;
        Piece piece = new Piece();
        piece.moveTo = moveTo;
        piece.entity = d;
        piece.life = life;
        piece.end = end;
        piece.endTicks = endTicks;
        pieces.add(piece);
    }

    private void spawnTrail(LivingEntity p, TrimSettings s, Location now, Location prev, boolean moving) {
        if (pieces.size() >= MAX_PIECES) return;
        float size = (float) s.size;
        Color color = s.sample(flowHue());
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        Vector move = (prev != null && moving) ? now.toVector().subtract(prev.toVector()).setY(0) : forward(now.getYaw()).multiply(0.1);
        double dist = move.length();
        float moveYaw = (float) Math.toDegrees(Math.atan2(-move.getX(), move.getZ()));

        switch (s.trailShape) {
            case STREAM, COMET -> {
                Location l = (prev != null && moving ? prev.clone().add(move.clone().multiply(0.5)) : now.clone()).add(0, 0.04, 0);
                l.setYaw(moveYaw);
                l.setPitch(0);
                float len = (float) Math.max(0.7, Math.min(2.6, dist * 5)) * size;
                float width = (s.trailShape == TrailShape.COMET ? 1.0f : 0.75f) * size;
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_streak", List.of(color)),
                        tf(width, 1, len), Display.Billboard.FIXED, 0);
                addPiece(d, 20, tf(0, -0.02f, 0, new Quaternionf(), 0.08f * width, 1, len * 1.35f), 18);
            }
            case DOUBLE -> {
                Vector right = right(now.getYaw());
                for (int side = -1; side <= 1; side += 2) {
                    Location l = (prev != null && moving ? prev.clone().add(move.clone().multiply(0.5)) : now.clone())
                            .add(right.clone().multiply(0.2 * side)).add(0, 0.04, 0);
                    l.setYaw(moveYaw);
                    l.setPitch(0);
                    float len = (float) Math.max(0.6, Math.min(2.2, dist * 5)) * size;
                    Color c = side < 0 ? s.sample(0) : s.sample(1);
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_streak", List.of(c)),
                            tf(0.5f * size, 1, len), Display.Billboard.FIXED, 0);
                    addPiece(d, 18, tf(0, -0.02f, 0, new Quaternionf(), 0.05f, 1, len * 1.3f), 16);
                }
            }
            case SPARKS, CLOUD -> {
                int n = s.trailShape == TrailShape.CLOUD ? 2 : 1;
                for (int i = 0; i < n; i++) {
                    Location l = now.clone().add(rnd.nextDouble(-0.3, 0.3), rnd.nextDouble(0.4, 1.4), rnd.nextDouble(-0.3, 0.3));
                    float bs = (float) rnd.nextDouble(0.25, s.trailShape == TrailShape.CLOUD ? 0.6 : 0.4) * size;
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_blob", List.of(s.sample(rnd.nextDouble()))),
                            tf(bs, bs, bs), Display.Billboard.CENTER, 0);
                    // droplets fall and shrink, like splashes of liquid
                    Location fall = l.clone().add(rnd.nextDouble(-0.4, 0.4), -0.9, rnd.nextDouble(-0.4, 0.4));
                    addPiece(d, 14, tf(0.02f, 0.02f, 0.02f), 13, fall);
                }
            }
            case SPIRAL -> {
                for (int k = 0; k < 2; k++) {
                    double a = tick * 0.35 + k * Math.PI;
                    double y = 0.2 + ((tick * 0.03 + k * 0.5) % 1.0) * 1.3;
                    Location l = now.clone().add(Math.cos(a) * 0.5 * size, y, Math.sin(a) * 0.5 * size);
                    float bs = 0.32f * size;
                    ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_blob", List.of(s.sample(k == 0 ? 0 : 1))),
                            tf(bs, bs, bs), Display.Billboard.CENTER, 0);
                    addPiece(d, 16, tf(0.02f, 0.02f, 0.02f), 15, l.clone().add(0, 0.35, 0));
                }
            }
            case ZIGZAG -> {
                int flip = (tick / 2) % 2 == 0 ? 1 : -1;
                Location l = (prev != null && moving ? prev.clone().add(move.clone().multiply(0.5)) : now.clone())
                        .add(right(now.getYaw()).multiply(0.22 * flip)).add(0, 0.04, 0);
                l.setYaw(moveYaw + 32 * flip);
                l.setPitch(0);
                float len = (float) Math.max(0.6, Math.min(2.0, dist * 4.5)) * size;
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_streak", List.of(color)),
                        tf(0.55f * size, 1, len), Display.Billboard.FIXED, 0);
                addPiece(d, 18, tf(0, -0.02f, 0, new Quaternionf(), 0.06f, 1, len * 1.3f), 16);
            }
            case RINGS -> {
                if (!moving || tick % 6 != 0) return;
                Location l = now.clone().add(0, 0.05, 0);
                l.setYaw(0);
                l.setPitch(0);
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_ring", ringColors(s, flowHue(), 1)),
                        tf(0.2f * size, 0.5f, 0.2f * size), Display.Billboard.FIXED, 0);
                addPiece(d, 16, tf(0.75f * size, 0.05f, 0.75f * size), 15);
            }
            case FOOTSTEPS -> {
                if (!moving || tick % 6 != 0 || !p.isOnGround()) return;
                int step = footsteps.merge(p.getUniqueId(), 1, Integer::sum);
                int side = step % 2 == 0 ? 1 : -1;
                Location l = now.clone().add(right(now.getYaw()).multiply(0.17 * side)).add(0, 0.03, 0);
                l.setYaw(moveYaw);
                l.setPitch(0);
                ItemDisplay d = spawnDisplay(p, l, liquidItem("liquid_puddle", List.of(color)),
                        tf(0.15f * size, 1, 0.15f * size), Display.Billboard.FIXED, 0);
                if (d == null) return;
                Piece piece = new Piece();
                piece.entity = d;
                piece.life = 34;
                piece.end = tf(0.75f * size, 1, 0.75f * size);   // splash spreads out
                piece.endTicks = 6;
                piece.end2 = tf(0.02f, 1, 0.02f);                  // then soaks away
                piece.end2At = 18;
                piece.end2Ticks = 14;
                pieces.add(piece);
            }
        }
    }
}
