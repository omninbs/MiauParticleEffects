package org.miau.particleeffects.animation;

import java.util.Locale;

public record AnimationToken(AnimCategory category, String style) {

    public AnimationToken {
        if (category == null) {
            throw new IllegalArgumentException("动画类别不能为空");
        }
        if (style == null || !category.hasStyle(style)) {
            throw new IllegalArgumentException("动画类别 '" + category.id() + "' 不支持样式 '" + style + "'");
        }
    }

    public static AnimationToken parse(String raw) {
        String s = raw == null ? "" : raw.toLowerCase(Locale.ROOT);
        int idx = s.indexOf(':');
        if (idx <= 0) {
            throw new IllegalArgumentException("动画令牌格式应为 '类别:样式'，收到 '" + raw + "'");
        }
        String cat = s.substring(0, idx);
        String style = s.substring(idx + 1);
        AnimCategory category = AnimCategory.byId(cat);
        if (category == null) {
            throw new IllegalArgumentException("未知动画类别 '" + cat + "'，可用： " + AnimCategory.allIds());
        }
        return new AnimationToken(category, style);
    }

    @Override
    public String toString() {
        return category.id() + ":" + style;
    }
}