package org.miau.particleeffects.model;

public record Orientation(float yaw, float pitch, float roll) {

    public static final Orientation SOUTH = new Orientation(0f, 0f, 0f);

    public Orientation {
        if (Float.isNaN(yaw) || Float.isNaN(pitch) || Float.isNaN(roll)) {
            throw new IllegalArgumentException("朝向角度不能为 NaN");
        }
    }
}