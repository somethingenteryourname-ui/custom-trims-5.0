package dev.customtrims;

import dev.customtrims.command.TrimCommand;
import dev.customtrims.effect.CycleTask;
import dev.customtrims.effect.EffectTask;
import dev.customtrims.effect.Wearers;
import dev.customtrims.effect.LiquidTask;
import dev.customtrims.pack.PackBuilder;
import dev.customtrims.pack.PackServer;
import dev.customtrims.trim.OverlayManager;
import dev.customtrims.trim.SmithingListener;
import dev.customtrims.trim.TrimItems;
import dev.customtrims.trim.TrimManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class CustomTrimsPlugin extends JavaPlugin {

    private TrimManager trimManager;
    private OverlayManager overlays;
    private TrimItems trimItems;
    private Wearers wearers;
    private CycleTask cycleTask;
    private PackBuilder packBuilder;
    private PackServer packServer;
    private EffectTask effectTask;
    private LiquidTask liquidTask;
    private final Set<UUID> hidden = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        LiquidTask.removeAllTagged();   // leftovers from a crash or /reload
        overlays = new OverlayManager(this);
        trimManager = new TrimManager(this);
        trimItems = new TrimItems(this);
        wearers = new Wearers(this);
        preregisterPresets();

        packBuilder = new PackBuilder(this);
        packBuilder.build();
        packServer = new PackServer(this);
        packServer.start();
        getServer().getPluginManager().registerEvents(packServer, this);
        getServer().getPluginManager().registerEvents(new SmithingListener(this), this);
        getServer().getPluginManager().registerEvents(wearers, this);
        getServer().getScheduler().runTaskTimer(this, wearers::scan, 40L, 40L);
        trimItems.registerRecipes();

        TrimCommand command = new TrimCommand(this);
        PluginCommand pc = getCommand("ctrim");
        if (pc != null) {
            pc.setExecutor(command);
            pc.setTabCompleter(command);
        }

        startEffects();
        getLogger().info("CustomTrims enabled: " + trimManager.getPresets().size() + " presets, "
                + TrimManager.CUSTOM_PATTERNS.size() + " new patterns, "
                + trimManager.getCustomMaterials().size() + " custom materials.");
    }

    @Override
    public void onDisable() {
        if (effectTask != null) {
            effectTask.cancel();
            effectTask = null;
        }
        if (liquidTask != null) {
            liquidTask.shutdown();
            liquidTask = null;
        }
        if (cycleTask != null) {
            cycleTask.cancel();
            cycleTask = null;
        }
        if (packServer != null) packServer.stop();
        if (trimItems != null) trimItems.unregisterRecipes();
    }

    private void startEffects() {
        if (effectTask != null) effectTask.cancel();
        int interval = Math.max(1, Math.min(20, getConfig().getInt("tick-interval", 2)));
        effectTask = new EffectTask(this, interval);
        effectTask.runTaskTimer(this, 20L, interval);

        if (liquidTask != null) liquidTask.shutdown();
        liquidTask = new LiquidTask(this);
        liquidTask.runTaskTimer(this, 20L, 1L);

        if (cycleTask != null) cycleTask.cancel();
        cycleTask = new CycleTask(this);
        cycleTask.runTaskTimer(this, 20L, 10L);
    }

    private void preregisterPresets() {
        for (dev.customtrims.trim.TrimSettings p : trimManager.getPresets().values()) {
            overlays.preregister(trimManager.validLayers(p.copy()));
        }
    }

    public void reloadAll() {
        reloadConfig();
        trimManager.reload();
        preregisterPresets();
        String oldHash = packBuilder.getSha1Hex();
        packBuilder.build();
        packServer.start();
        if (!packBuilder.getSha1Hex().equals(oldHash)) packServer.sendToAll();
        trimItems.registerRecipes();
        startEffects();
    }

    /** Rebuilds the resource pack (e.g. a new stacked-trim combo was made) and sends it to everyone online. */
    public void rebuildPackAndResend() {
        String oldHash = packBuilder.getSha1Hex();
        packBuilder.build();
        if (!packBuilder.getSha1Hex().equals(oldHash)) packServer.sendToAll();
    }

    public void invalidate(org.bukkit.entity.Entity p) {
        if (effectTask != null) effectTask.invalidate(p.getUniqueId());
        if (liquidTask != null) liquidTask.invalidate(p.getUniqueId());
    }

    private org.bukkit.NamespacedKey selfViewKey() {
        return new org.bukkit.NamespacedKey(this, "selfview");
    }

    /** Whether this player sees their own aura (off by default so it doesn't block their view). */
    public boolean seesOwnAura(Player p) {
        Byte v = p.getPersistentDataContainer().get(selfViewKey(), org.bukkit.persistence.PersistentDataType.BYTE);
        return v != null ? v == 1 : getConfig().getBoolean("see-own-aura-by-default", false);
    }

    public boolean toggleSelfView(Player p) {
        boolean now = !seesOwnAura(p);
        p.getPersistentDataContainer().set(selfViewKey(), org.bukkit.persistence.PersistentDataType.BYTE, (byte) (now ? 1 : 0));
        invalidate(p);
        return now;
    }

    /** How long smooth changes take (cycle mode, new colors, new auras), in ticks. */
    public int blendTicks() {
        return Math.max(2, (int) Math.round(getConfig().getDouble("blend-seconds", 1.5) * 20));
    }

    public Wearers getWearers() {
        return wearers;
    }

    public boolean isHidden(UUID id) {
        return hidden.contains(id);
    }

    /** @return true if effects are now hidden */
    public boolean toggleHidden(UUID id) {
        if (hidden.remove(id)) return false;
        hidden.add(id);
        return true;
    }

    public TrimManager getTrimManager() {
        return trimManager;
    }

    public OverlayManager getOverlays() {
        return overlays;
    }

    public TrimItems getTrimItems() {
        return trimItems;
    }

    public PackBuilder getPackBuilder() {
        return packBuilder;
    }

    public PackServer getPackServer() {
        return packServer;
    }
}
