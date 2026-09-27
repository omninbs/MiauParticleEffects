package org.miau.particleeffects.model;

import java.util.Locale;

/**
 * 颜色模式：纯。/ 静态渐变色 / 彩虹渐变（流动）/ 彩虹颜色（整体随时间循环）。 */
public enum ColorMode {

    SOLID("solid"),
    GRADIENT("gradient"),
    RAINBOW_GRADIENT("rainbow-gradient"),
    RAINBOW_COLOR("rainbow-color");

    private final String id;

    ColorMode(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ColorMode parse(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        for (ColorMode mode : values()) {
            if (mode.id.equals(s)) {
                return mode;
            }
        }
        return null;
    }
}
