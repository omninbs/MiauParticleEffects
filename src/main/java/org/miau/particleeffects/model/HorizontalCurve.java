package org.miau.particleeffects.model;

public enum HorizontalCurve {

    LINE("line"),
    ARC("arc"),
    SINE("sine");

    private final String id;

    HorizontalCurve(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static HorizontalCurve parse(String raw) {
        for (HorizontalCurve curve : values()) {
            if (curve.id.equalsIgnoreCase(raw)) {
                return curve;
            }
        }
        return null;
    }

    public static String allIds() {
        StringBuilder sb = new StringBuilder();
        for (HorizontalCurve curve : values()) {
            if (!sb.isEmpty()) {
                sb.append('/');
            }
            sb.append(curve.id);
        }
        return sb.toString();
    }
}