package org.miau.particleeffects.model;

import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.Easings;

public record MoveSpec(Vec3 delta, int durationTicks, Easing curve) {

    public MoveSpec {
        if (delta == null) {
            throw new IllegalArgumentException("移动位移不能为空");
        }
        if (durationTicks < 0) {
            throw new IllegalArgumentException("移动时长不能为负");
        }
        if (curve == null) {
            curve = Easings.EASE_OUT;
        }
    }
}