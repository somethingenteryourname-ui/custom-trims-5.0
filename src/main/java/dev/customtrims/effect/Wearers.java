package dev.customtrims.effect;

import dev.customtrims.CustomTrimsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Everyone whose effects should be drawn: online players plus armor stands wearing custom trims. */
public final class Wearers implements Listener {

    private final CustomTrimsPlugin plugin;
    private final Set<UUID> stands = new HashSet<>();

    public Wearers(CustomTrimsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean standsEnabled() {
        return plugin.getConfig().getBoolean("armor-stands", true);
    }

    public List<LivingEntity> all() {
        List<LivingEntity> out = new ArrayList<>(Bukkit.getOnlinePlayers());
        if (!standsEnabled()) return out;
        Iterator<UUID> it = stands.iterator();
        while (it.hasNext()) {
            Entity e = Bukkit.getEntity(it.next());
            if (e instanceof ArmorStand a && a.isValid()) out.add(a);
            else it.remove();
        }
        return out;
    }

    /** Looks for armor stands wearing custom trims in all loaded chunks. */
    public void scan() {
        if (!standsEnabled()) {
            stands.clear();
            return;
        }
        Set<UUID> found = new HashSet<>();
        for (World w : Bukkit.getWorlds()) {
            for (ArmorStand a : w.getEntitiesByClass(ArmorStand.class)) {
                if (plugin.getTrimManager().getActive(a) != null) found.add(a.getUniqueId());
            }
        }
        stands.clear();
        stands.addAll(found);
    }

    /** When someone puts armor on (or takes it off) a stand, check again right away. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStandChange(PlayerArmorStandManipulateEvent event) {
        ArmorStand stand = event.getRightClicked();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (stand.isValid() && plugin.getTrimManager().getActive(stand) != null) stands.add(stand.getUniqueId());
            else stands.remove(stand.getUniqueId());
            plugin.invalidate(stand);
        }, 1L);
    }
}
