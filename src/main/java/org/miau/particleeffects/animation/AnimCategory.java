package org.miau.particleeffects.animation;

import java.util.Locale;
import java.util.Set;

public enum AnimCategory {

    SCALE("scale", Set.of("shrink", "enlarge")),
    SLIDE("slide", Set.of("up", "down", "left", "right")),
    SPACING("spacing", Set.of("converge", "disperse")),
    CLARITY("clarity", Set.of("clear", "blur"));

    private final String id;
    private final Set<String> styles;

    AnimCategory(String id, Set<String> styles) {
        this.id = id;
        this.styles = styles;
    }

    public String id() {
        return id;
    }

    public Set<String> styles() {
        return styles;
    }

    public boolean hasStyle(String style) {
        return styles.contains(style);
    }

    public static AnimCategory byId(String id) {
        for (AnimCategory category : values()) {
            if (category.id.equals(id)) {
                return category;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return id;
    }

    public static AnimCategory parse(String value) {
        for (AnimCategory category : values()) {
            if (category.id.equalsIgnoreCase(value) || category.name().equalsIgnoreCase(value)) {
                return category;
            }
        }
        return null;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (AnimCategory category : values()) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(category.id);
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}