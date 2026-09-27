package org.miau.particleeffects.model;

public enum EffectType {

    CUBE("cube"),
    TETRA("tetra"),
    EXPLOSION("explosion"),
    WAVE("wave");

    private final String id;

    EffectType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static EffectType parse(String raw) {
        for (EffectType type : values()) {
            if (type.id.equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}