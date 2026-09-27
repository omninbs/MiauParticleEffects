package org.miau.particleeffects.animation;

import java.util.Locale;

public enum FadeOption {

    NONE("none"),
    IN("in"),
    OUT("out"),
    BOTH("both");

    private final String id;

    FadeOption(String id) {
        this.id = id;
    }

    public boolean fadeIn() {
        return this == IN || this == BOTH;
    }

    public boolean fadeOut() {
        return this == OUT || this == BOTH;
    }

    public String id() {
        return id;
    }

    public static FadeOption parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("-", "");
        return switch (s) {
            case "none", "" -> NONE;
            case "in", "fadein" -> IN;
            case "out", "fadeout" -> OUT;
            case "both" -> BOTH;
            default -> null;
        };
    }
}