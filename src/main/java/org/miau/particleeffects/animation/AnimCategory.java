package org.miau.particleeffects.animation;

import java.util.Locale;
import java.util.Set;

/**
 * 动画类别：同一类别内只能选一个样式（互斥），不同类别可以叠加（并行）。
 *
 * <p>每个类别的样式都描述「从哪儿来 / 往哪儿去」，具体是入场还是出场由 {@code in=} / {@code out=}
 * 决定，模组自动做镜像。样式可以省略（如 {@code in=typewriter}），此时取
 * {@link #defaultStyle()}。</p>
 */
public enum AnimCategory {

    /** 整体缩放：shrink 由大变小，enlarge 由小变大。 */
    SCALE("scale", Set.of("shrink", "enlarge"), "enlarge"),
    /** 整体平移：上/下/左/右滑入滑出。 */
    SLIDE("slide", Set.of("up", "down", "left", "right"), "up"),
    /** 字间距：converge 由散到聚，disperse 由聚到散。 */
    SPACING("spacing", Set.of("converge", "disperse"), "converge"),
    /** 清晰度：clear 由糊到清，blur 由清到糊（模糊 = 稀疏 + 抖动 + 降低亮度）。 */
    CLARITY("clarity", Set.of("clear", "blur"), "clear"),
    /** 逐字打字：left/right 从一侧逐字出现，center 从中间向两侧。 */
    TYPEWRITER("typewriter", Set.of("left", "right", "center"), "left"),
    /** 擦除/显现：按一条推过的边线截断显示（带柔和边缘），与逐字打字的区别是连续而非按字。 */
    WIPE("wipe", Set.of("left", "right", "up", "down"), "left"),
    /** 翻转：horizontal 绕竖直轴、vertical 绕水平轴翻。 */
    FLIP("flip", Set.of("horizontal", "vertical"), "horizontal"),
    /** 爆散/聚拢：gather 由随机远处聚拢（出场则散开），burst 由中心炸开（出场则塌回中心）。 */
    SCATTER("scatter", Set.of("gather", "burst"), "gather"),
    /** 弹跳落位：drop 从上方带弹跳落下，rise 从下方带弹跳升起。 */
    BOUNCE("bounce", Set.of("drop", "rise"), "drop"),
    /** 推近/拉远：in 由小推近，out 由大拉远；粒子尺寸同时变化，观感与 scale 不同。 */
    ZOOM("zoom", Set.of("in", "out"), "in"),
    /** 旋转入场：roll 绕视线轴转，yaw 绕竖直轴转（水平压扁），pitch 绕水平轴转（垂直压扁）。 */
    ROTATE("rotate", Set.of("roll", "yaw", "pitch"), "roll"),
    /** 坠落：down/up/left/right 直线加速落下（无弹跳）。 */
    DROP("drop", Set.of("down", "up", "left", "right"), "down");

    private final String id;
    private final Set<String> styles;
    private final String defaultStyle;

    AnimCategory(String id, Set<String> styles, String defaultStyle) {
        this.id = id;
        this.styles = styles;
        this.defaultStyle = defaultStyle;
    }

    public String id() {
        return id;
    }

    public Set<String> styles() {
        return styles;
    }

    /** 省略样式时使用的默认样式。 */
    public String defaultStyle() {
        return defaultStyle;
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

    /** 形如 {@code slide:up, scale:enlarge} 的全部合法写法，用于命令补全与帮助。 */
    public static String allTokens() {
        StringBuilder sb = new StringBuilder();
        for (AnimCategory category : values()) {
            for (String style : category.styles) {
                if (!sb.isEmpty()) {
                    sb.append(", ");
                }
                sb.append(category.id).append(':').append(style);
            }
        }
        return sb.toString();
    }
}
