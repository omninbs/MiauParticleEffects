package org.miau.particleeffects.animation;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class Easings {

    private static final Map<String, Easing> BY_NAME = new LinkedHashMap<>();
    private static final Map<Easing, String> NAME_BY_VALUE = new LinkedHashMap<>();

    private Easings() {
    }

    private static Easing reg(String name, Easing easing) {
        BY_NAME.put(name, easing);
        NAME_BY_VALUE.put(easing, name);
        return easing;
    }

    public static final Easing LINEAR = reg("linear", t -> clamp(t));

    public static final Easing EASE_IN = reg("ease-in", t -> {
        float x = clamp(t);
        return x * x;
    });

    public static final Easing EASE_OUT = reg("ease-out", t -> {
        float x = clamp(t);
        return 1f - (1f - x) * (1f - x);
    });

    public static final Easing EASE_IN_OUT = reg("ease-in-out", t -> {
        float x = clamp(t);
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    });

    public static final Easing QUAD_IN = reg("quad-in", t -> {
        float x = clamp(t);
        return x * x;
    });

    public static final Easing QUAD_OUT = reg("quad-out", t -> {
        float x = clamp(t);
        return 1f - (1f - x) * (1f - x);
    });

    public static final Easing QUAD_IN_OUT = reg("quad-in-out", t -> {
        float x = clamp(t);
        return x < 0.5f ? 2f * x * x : 1f - (float) Math.pow(-2f * x + 2f, 2) / 2f;
    });

    public static final Easing CUBIC_IN = reg("cubic-in", t -> {
        float x = clamp(t);
        return x * x * x;
    });

    public static final Easing CUBIC_OUT = reg("cubic-out", t -> {
        float x = clamp(t);
        return 1f - (float) Math.pow(1f - x, 3);
    });

    public static final Easing CUBIC_IN_OUT = reg("cubic-in-out", t -> {
        float x = clamp(t);
        return x < 0.5f ? 4f * x * x * x : 1f - (float) Math.pow(-2f * x + 2f, 3) / 2f;
    });

    public static final Easing SIN_WAVE = reg("sine", t -> {
        float x = clamp(t);
        return (float) Math.sin(x * Math.PI);
    });

    public static final Easing BOUNCE = reg("bounce", t -> bounce(clamp(t)));

    private static float clamp(float t) {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        return t;
    }

    private static float bounce(float x) {
        float n1 = 7.5625f;
        float d1 = 2.75f;
        if (x < 1f / d1) {
            return n1 * x * x;
        }
        if (x < 2f / d1) {
            x -= 1.5f / d1;
            return n1 * x * x + 0.75f;
        }
        if (x < 2.5f / d1) {
            x -= 2.25f / d1;
            return n1 * x * x + 0.9375f;
        }
        x -= 2.625f / d1;
        return n1 * x * x + 0.984375f;
    }

    public static Easing byName(String name) {
        return BY_NAME.get(name == null ? null : name.toLowerCase(Locale.ROOT));
    }

    public static String nameOf(Easing easing) {
        String name = NAME_BY_VALUE.get(easing);
        return name != null ? name : "ease-out";
    }

    public static Map<String, Easing> names() {
        return Collections.unmodifiableMap(BY_NAME);
    }
}