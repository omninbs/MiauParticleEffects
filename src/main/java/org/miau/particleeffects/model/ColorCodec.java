package org.miau.particleeffects.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ColorCodec {

    private static final Map<String, Integer> NAMED = Map.ofEntries(
            Map.entry("white", 0xFFFFFFFF),
            Map.entry("black", 0xFF000000),
            Map.entry("red", 0xFFFF0000),
            Map.entry("green", 0xFF00FF00),
            Map.entry("blue", 0xFF0000FF),
            Map.entry("yellow", 0xFFFFFF00),
            Map.entry("cyan", 0xFF00FFFF),
            Map.entry("magenta", 0xFFFF00FF),
            Map.entry("orange", 0xFFFFA500),
            Map.entry("gray", 0xFF808080),
            Map.entry("grey", 0xFF808080),
            Map.entry("lightgray", 0xFFC0C0C0),
            Map.entry("darkgray", 0xFF404040));

    private ColorCodec() {
    }

    /**
     * 解析颜色。ARGB。支。"#RRGGBB"。#RRGGBBAA"。#RGB" 与预置颜色名。     */
    public static int parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("颜色不能为空");
        }
        String s = raw.trim();
        if (s.startsWith("#")) {
            String hex = s.substring(1);
            if (hex.length() == 3) {
                char r = hex.charAt(0);
                char g = hex.charAt(1);
                char b = hex.charAt(2);
                return 0xFF000000
                        | Integer.parseInt("" + r + r, 16) << 16
                        | Integer.parseInt("" + g + g, 16) << 8
                        | Integer.parseInt("" + b + b, 16);
            }
            if (hex.length() == 6) {
                int rgb = Integer.parseInt(hex, 16);
                return 0xFF000000 | rgb;
            }
            if (hex.length() == 8) {
                int a = Integer.parseInt(hex.substring(0, 2), 16);
                int rgb = Integer.parseInt(hex.substring(2), 16);
                return (a << 24) | rgb;
            }
            throw new IllegalArgumentException("颜色格式错误 '#" + hex + "'，应。#RGB。RRGGBB 。#RRGGBBAA");
        }
        Integer named = NAMED.get(s.toLowerCase(Locale.ROOT));
        if (named != null) {
            return named;
        }
        throw new IllegalArgumentException("未知颜色 '" + raw + "'");
    }

    public static float alpha(int argb) {
        return (argb >>> 24) / 255f;
    }

    public static float red(int argb) {
        return ((argb >> 16) & 0xFF) / 255f;
    }

    public static float green(int argb) {
        return ((argb >> 8) & 0xFF) / 255f;
    }

    public static float blue(int argb) {
        return (argb & 0xFF) / 255f;
    }

    /** 彩虹循环周期（tick），一。60 tick = 3 秒。*/
    public static final float RAINBOW_CYCLE_TICKS = 60f;

    /**
     * 解析逗号分隔的渐变色列表，格。"#RRGGBB,#RRGGBB[,...]"，至。2 个颜色。     */
    public static List<Integer> parseGradient(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("渐变色不能为空");
        }
        String[] parts = raw.split(",");
        List<Integer> colors = new ArrayList<>();
        for (String part : parts) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            colors.add(parse(s) & 0xFFFFFF);
        }
        if (colors.size() < 2) {
            throw new IllegalArgumentException("渐变色至少需要 2 个颜色，格式 #RRGGBB,#RRGGBB[,...]");
        }
        return List.copyOf(colors);
    }

    /**
     * 在渐变色的多个颜色间插值采样，t∈[0,1]。     */
    public static int sampleGradient(List<Integer> colors, float t) {
        if (colors == null || colors.isEmpty()) {
            return 0xFFFFFF;
        }
        if (colors.size() == 1) {
            return colors.get(0);
        }
        float x = Math.max(0f, Math.min(1f, t));
        float pos = x * (colors.size() - 1);
        int i = (int) pos;
        if (i >= colors.size() - 1) {
            return colors.get(colors.size() - 1);
        }
        return lerpColor(colors.get(i), colors.get(i + 1), pos - i);
    }

    public static int lerpColor(int a, int b, float t) {
        if (t <= 0f) {
            return a;
        }
        if (t >= 1f) {
            return b;
        }
        int ar = (a >> 16) & 0xFF;
        int ag = (a >> 8) & 0xFF;
        int ab = a & 0xFF;
        int br = (b >> 16) & 0xFF;
        int bg = (b >> 8) & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int g = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /**
     * HSV→RGB（饱和度/明度固定。1，即彩虹色），hue∈[0,1)。     */
    public static int hsvToRgb(float hue) {
        float h = ((hue % 1f) + 1f) % 1f;
        int sector = (int) (h * 6f);
        float f = h * 6f - sector;
        int r, g, b;
        switch (sector) {
            case 0 -> {
                r = 255;
                g = (int) (f * 255f);
                b = 0;
            }
            case 1 -> {
                r = (int) ((1f - f) * 255f);
                g = 255;
                b = 0;
            }
            case 2 -> {
                r = 0;
                g = 255;
                b = (int) (f * 255f);
            }
            case 3 -> {
                r = 0;
                g = (int) ((1f - f) * 255f);
                b = 255;
            }
            case 4 -> {
                r = (int) (f * 255f);
                g = 0;
                b = 255;
            }
            default -> {
                r = 255;
                g = 0;
                b = (int) ((1f - f) * 255f);
            }
        }
        return (r << 16) | (g << 8) | b;
    }

    /**
     * 当前世界时间的彩虹相位（0..1），避免大时间值下浮点精度问题。     */
    public static float rainbowPhase(long worldTime) {
        return (worldTime % (long) RAINBOW_CYCLE_TICKS) / RAINBOW_CYCLE_TICKS;
    }
}