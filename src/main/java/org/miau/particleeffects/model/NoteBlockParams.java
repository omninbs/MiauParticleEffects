package org.miau.particleeffects.model;

import net.minecraft.util.math.Vec3d;

public record NoteBlockParams(
        Vec3d center,
        float radius,
        int trailCount,
        HorizontalCurve curve,
        int ballCount,
        float maxJumpHeight,
        boolean force) {

    public static final float DEFAULT_RADIUS = 16f;
    public static final int DEFAULT_TRAIL = 24;
    public static final HorizontalCurve DEFAULT_CURVE = HorizontalCurve.ARC;
    public static final int DEFAULT_BALLS = 1;
    public static final float DEFAULT_HEIGHT = 3f;

    public NoteBlockParams {
        if (center == null) {
            throw new IllegalArgumentException("音符盒检测中心不能为空");
        }
        if (radius <= 0 || radius > 256) {
            throw new IllegalArgumentException("检测半径必须大于 0 且不超过 256");
        }
        if (trailCount < 2) {
            throw new IllegalArgumentException("拖尾长度至少为 2");
        }
        if (curve == null) {
            throw new IllegalArgumentException("水平运动曲线不能为空");
        }
        if (ballCount < 1 || ballCount > 8) {
            throw new IllegalArgumentException("弹力球数量必须在 1~8 之间");
        }
        if (maxJumpHeight <= 0 || maxJumpHeight > 64) {
            throw new IllegalArgumentException("跳动最大高度必须大于 0 且不超过 64");
        }
    }
}
