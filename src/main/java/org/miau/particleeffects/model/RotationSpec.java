package org.miau.particleeffects.model;

import org.joml.Vector3f;

public record RotationSpec(Vector3f axis, float degreesPerTick) {

    public RotationSpec {
        if (axis == null) {
            throw new IllegalArgumentException("旋转轴不能为空");
        }
        float lenSq = axis.lengthSquared();
        if (lenSq <= 1e-6f) {
            throw new IllegalArgumentException("旋转轴不能为零向量");
        }
    }
}