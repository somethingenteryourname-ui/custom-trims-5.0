package dev.customtrims.trim;

import java.util.Locale;

/** One extra trim layer (pattern + material) drawn on top of the armor. */
public final class Layer {

    public final String pattern;
    public final String material;

    public Layer(String pattern, String material) {
        this.pattern = pattern.toLowerCase(Locale.ROOT);
        this.material = material.toLowerCase(Locale.ROOT);
    }

    public String serialize() {
        return pattern + ":" + material;
    }

    public static Layer parse(String raw) {
        String[] parts = raw.trim().split(":");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) return null;
        return new Layer(parts[0], parts[1]);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Layer l && l.pattern.equals(pattern) && l.material.equals(material);
    }

    @Override
    public int hashCode() {
        return pattern.hashCode() * 31 + material.hashCode();
    }
}
