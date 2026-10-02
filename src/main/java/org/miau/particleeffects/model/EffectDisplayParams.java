package org.miau.particleeffects.model;

import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.animation.AnimCategory;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.FadeOption;

import java.util.List;

public record EffectDisplayParams(
        EffectType type,
        Vec3 pos,
        float size,
        float waveSpeed,
        int colorArgb,
        ColorMode colorMode,
        List<Integer> gradientColors,
        Orientation orientation,
        RotationSpec rotation,
        int durationTicks,
        int enterTicks,
        int exitTicks,
        int delayTicks,
        Easing curve,
        AnimationSet entry,
        AnimationSet exit,
        FadeOption fade,
        MoveSpec move,
        String id,
        boolean force) {

    public EffectDisplayParams {
        if (type == null) {
            throw new IllegalArgumentException("特效类型不能为空");
        }
        if (pos == null) {
            throw new IllegalArgumentException("特效位置不能为空");
        }
        if (size <= 0 || size > 256) {
            throw new IllegalArgumentException("特效尺寸必须大于 0 且不超过 256");
        }
        if (waveSpeed < 0) {
            throw new IllegalArgumentException("水波展开速度不能为负");
        }
        if (curve == null) {
            throw new IllegalArgumentException("动画曲线不能为空");
        }
        if (entry == null) {
            entry = AnimationSet.EMPTY;
        }
        if (exit == null) {
            exit = AnimationSet.EMPTY;
        }
        if (fade == null) {
            fade = FadeOption.NONE;
        }
        if (orientation == null) {
            orientation = new Orientation(0f, 0f, 0f);
        }
        if (colorMode == null) {
            colorMode = ColorMode.SOLID;
        }
        if (gradientColors == null) {
            gradientColors = List.of();
        }
        if (durationTicks < 0 || enterTicks < 0 || exitTicks < 0 || delayTicks < 0) {
            throw new IllegalArgumentException("时长参数不能为负");
        }
        if (!entry.containsOnlyCategories(AnimCategory.SCALE) || !exit.containsOnlyCategories(AnimCategory.SCALE)) {
            throw new IllegalArgumentException("附加特效动画仅支持 scale 类与淡入淡出");
        }
        if (colorMode == ColorMode.GRADIENT && gradientColors.size() < 2) {
            throw new IllegalArgumentException("渐变色模式至少需要 2 个颜色");
        }
    }

    public EffectDisplayParams withId(String newId) {
        return new EffectDisplayParams(
                type, pos, size, waveSpeed, colorArgb, colorMode, gradientColors, orientation, rotation,
                durationTicks, enterTicks, exitTicks, delayTicks,
                curve, entry, exit, fade, move, newId, force);
    }

    public EffectDisplayParams withDelay(int addedTicks) {
        if (addedTicks <= 0) {
            return this;
        }
        return new EffectDisplayParams(
                type, pos, size, waveSpeed, colorArgb, colorMode, gradientColors, orientation, rotation,
                durationTicks, enterTicks, exitTicks, delayTicks + addedTicks,
                curve, entry, exit, fade, move, id, force);
    }

    public int totalTicks() {
        return delayTicks + enterTicks + durationTicks + exitTicks;
    }
}
