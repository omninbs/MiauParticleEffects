package org.miau.particleeffects.model;

import java.util.Locale;

public enum ClearScope {

    ALL("all"),
    TEXT("text"),
    EFFECT("effect");

    private final String id;

    ClearScope(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ClearScope parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        for (ClearScope scope : values()) {
            if (scope.id.equals(s)) {
                return scope;
            }
        }
        return null;
    }
}