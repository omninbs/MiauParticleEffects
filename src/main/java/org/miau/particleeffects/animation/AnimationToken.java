package org.miau.particleeffects.animation;

import java.util.Locale;

/**
 * 一个动画令牌：类别 + 样式。
 *
 * <p>书写时可以省略样式（{@code in=typewriter}），此时使用类别的默认样式；
 * 样式也接受同义词（{@code fadedown} 之类）以外的常见简写，见 {@link #normalizeStyle}。</p>
 */
public record AnimationToken(AnimCategory category, String style) {

    public AnimationToken {
        if (category == null) {
            throw new IllegalArgumentException("动画类别不能为空");
        }
        style = normalizeStyle(category, style);
        if (style == null || !category.hasStyle(style)) {
            throw new IllegalArgumentException("动画类别 '" + category.id() + "' 不支持样式 '"
                    + style + "'，可用：" + String.join(" / ", category.styles()));
        }
    }

    /** 兼容常见同义写法，同时补上省略的样式。返回 null 表示无法识别。 */
    private static String normalizeStyle(AnimCategory category, String raw) {
        if (raw == null || raw.isBlank()) {
            return category.defaultStyle();
        }
        String s = raw.trim().toLowerCase(Locale.ROOT).replace("-", "").replace("_", "");
        return switch (s) {
            // 通用方向同义词
            case "top", "north" -> "up";
            case "bottom", "south" -> "down";
            case "east" -> "right";
            case "west" -> "left";
            // scale
            case "small", "grow", "in" -> category == AnimCategory.ZOOM ? "in" : "enlarge";
            case "big", "shrink", "out" -> category == AnimCategory.ZOOM ? "out" : "shrink";
            default -> category.hasStyle(s) ? s : raw.trim().toLowerCase(Locale.ROOT);
        };
    }

    public static AnimationToken parse(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT).trim();
        if (s.isEmpty()) {
            throw new IllegalArgumentException("动画令牌不能为空");
        }
        int idx = s.indexOf(':');
        String cat = idx < 0 ? s : s.substring(0, idx);
        String style = idx < 0 ? "" : s.substring(idx + 1);
        AnimCategory category = AnimCategory.byId(cat);
        if (category == null) {
            throw new IllegalArgumentException("未知动画类别 '" + cat + "'，可用： " + AnimCategory.allIds());
        }
        return new AnimationToken(category, style);
    }

    /** 展示用：样式与默认值相同时只写类别，保持命令简短。 */
    @Override
    public String toString() {
        return style.equals(category.defaultStyle())
                ? category.id()
                : category.id() + ":" + style;
    }
}
