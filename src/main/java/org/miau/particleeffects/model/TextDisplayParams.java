package org.miau.particleeffects.model;

import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.FadeOption;

import java.util.List;

public record TextDisplayParams(
        String text,
        Vec3 pos,
        float scale,
        int colorArgb,
        ColorMode colorMode,
        List<Integer> gradientColors,
        Orientation orientation,
        int durationTicks,
        int enterTicks,
        int exitTicks,
        int delayTicks,
        Easing curve,
        AnimationSet entry,
        AnimationSet exit,
        FadeOption fade,
        float spread,
        double density,
        MoveSpec move,
        String id,
        boolean force,
        List<CharTiming> charTimings) {

    /** 逐字时间表条目上限（与文字长度上限一致）。 */
    public static final int MAX_CHARS = 256;

    public TextDisplayParams {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文字内容不能为空");
        }
        if (pos == null) {
            throw new IllegalArgumentException("显示位置不能为空");
        }
        if (curve == null) {
            throw new IllegalArgumentException("动画曲线不能为空");
        }
        if (entry == null) {
            throw new IllegalArgumentException("入场动画不能为空");
        }
        if (exit == null) {
            throw new IllegalArgumentException("出场动画不能为空");
        }
        if (fade == null) {
            fade = FadeOption.NONE;
        }
        if (orientation == null) {
            orientation = Orientation.SOUTH;
        }
        if (colorMode == null) {
            colorMode = ColorMode.SOLID;
        }
        if (gradientColors == null) {
            gradientColors = List.of();
        }
        if (charTimings == null) {
            charTimings = List.of();
        }
        if (charTimings.size() > MAX_CHARS) {
            throw new IllegalArgumentException("逐字时间表最多 " + MAX_CHARS + " 项");
        }
        if (scale <= 0 || scale > 64) {
            throw new IllegalArgumentException("缩放必须大于 0 且不超过 64");
        }
        if (durationTicks < 0 || enterTicks < 0 || exitTicks < 0 || delayTicks < 0) {
            throw new IllegalArgumentException("时长参数不能为负");
        }
        if (spread <= 0) {
            throw new IllegalArgumentException("间距倍率 spread 必须大于 0");
        }
        if (density <= 0 || density > 1) {
            throw new IllegalArgumentException("密度必须大于 0 且不超过 1");
        }
        if (text.trim().length() > 256) {
            throw new IllegalArgumentException("文字内容过长（最多 256 字符）");
        }
        if (colorMode == ColorMode.GRADIENT && gradientColors.size() < 2) {
            throw new IllegalArgumentException("渐变色模式至少需要 2 个颜色");
        }
    }

    public TextDisplayParams withId(String newId) {
        return new TextDisplayParams(
                text, pos, scale, colorArgb, colorMode, gradientColors, orientation,
                durationTicks, enterTicks, exitTicks, delayTicks,
                curve, entry, exit, fade, spread, density, move, newId, force, charTimings);
    }

    public TextDisplayParams withDelay(int addedTicks) {
        if (addedTicks <= 0) {
            return this;
        }
        return new TextDisplayParams(
                text, pos, scale, colorArgb, colorMode, gradientColors, orientation,
                durationTicks, enterTicks, exitTicks, delayTicks + addedTicks,
                curve, entry, exit, fade, spread, density, move, id, force, charTimings);
    }

    /** 是否逐字时间轴模式：每个字都有自己的开始时刻与显示时长。 */
    public boolean perChar() {
        return !charTimings.isEmpty();
    }

    /**
     * 逐字时间轴的整体跨度（不含 {@code enter}/{@code exit} 动画）：
     * 最后一字的 {@code start + duration}。
     */
    public int charSpanTicks() {
        int span = 0;
        for (CharTiming timing : charTimings) {
            if (timing.endTick() > span) {
                span = timing.endTick();
            }
        }
        return span;
    }

    /**
     * 整段占用的总时长（tick）。
     *
     * <p>逐字模式下 {@code durationTicks} 只是生成器算出的跨度，真实时长由时间表决定：
     * 最后一字退场完成后整句才删除，因此不含段级 {@code enterTicks}
     * （入场动画按字计算，时长已隐含在每字的 {@code start..start+duration} 里）。</p>
     */
    public int totalTicks() {
        if (perChar()) {
            return delayTicks + charSpanTicks() + exitTicks;
        }
        return delayTicks + enterTicks + durationTicks + exitTicks;
    }
}
