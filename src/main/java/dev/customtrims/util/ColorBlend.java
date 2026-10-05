package dev.customtrims.util;

import org.bukkit.Color;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Smoothly fades each wearer's colors to their new colors instead of switching instantly. */
public final class ColorBlend {

    private static final int POINTS = 8;

    private static final class State {
        List<Color> from;
        List<Color> to;
        long start;
    }

    private final Map<UUID, State> states = new HashMap<>();
    private int durationTicks;

    public ColorBlend(int durationTicks) {
        this.durationTicks = Math.max(1, durationTicks);
    }

    /** The colors to draw right now for this wearer. */
    public List<Color> colors(UUID id, List<Color> target, long tick) {
        State st = states.get(id);
        if (st == null) {
            st = new State();
            st.from = target;
            st.to = target;
            st.start = tick - durationTicks;
            states.put(id, st);
            return target;
        }
        if (!st.to.equals(target)) {
            st.from = current(st, tick);
            st.to = target;
            st.start = tick;
        }
        return current(st, tick);
    }

    public boolean isBlending(UUID id, long tick) {
        State st = states.get(id);
        return st != null && tick - st.start < durationTicks;
    }

    private List<Color> current(State st, long tick) {
        double t = (tick - st.start) / (double) durationTicks;
        if (t >= 1) return st.to;
        t = t * t * (3 - 2 * t);   // ease in and out
        List<Color> out = new ArrayList<>(POINTS);
        for (int i = 0; i < POINTS; i++) {
            double p = i / (double) (POINTS - 1);
            out.add(ColorUtil.lerp(ColorUtil.sample(st.from, p), ColorUtil.sample(st.to, p), t));
        }
        return out;
    }

    public void forget(UUID id) {
        states.remove(id);
    }

    public void retain(java.util.function.Predicate<UUID> keep) {
        states.keySet().removeIf(id -> !keep.test(id));
    }
}
