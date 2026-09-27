package org.miau.particleeffects.protocol;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import org.miau.particleeffects.animation.AnimationSet;
import org.miau.particleeffects.animation.Easing;
import org.miau.particleeffects.animation.Easings;
import org.miau.particleeffects.animation.FadeOption;
import org.miau.particleeffects.model.ClearScope;
import org.miau.particleeffects.model.ColorMode;
import org.miau.particleeffects.model.EffectDisplayParams;
import org.miau.particleeffects.model.EffectType;
import org.miau.particleeffects.model.MoveSpec;
import org.miau.particleeffects.model.Orientation;
import org.miau.particleeffects.model.RotationSpec;
import org.miau.particleeffects.model.TextDisplayParams;

import java.util.ArrayList;
import java.util.List;

public final class PayloadCodecs {

    private PayloadCodecs() {
    }

    static final PacketCodec<PacketByteBuf, Orientation> ORIENTATION = new PacketCodec<>() {
        @Override
        public Orientation decode(PacketByteBuf buf) {
            return new Orientation(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(PacketByteBuf buf, Orientation value) {
            buf.writeFloat(value.yaw());
            buf.writeFloat(value.pitch());
            buf.writeFloat(value.roll());
        }
    };

    static final PacketCodec<PacketByteBuf, AnimationSet> ANIMATION_SET = new PacketCodec<>() {
        @Override
        public AnimationSet decode(PacketByteBuf buf) {
            int count = buf.readVarInt();
            if (count <= 0) {
                return AnimationSet.EMPTY;
            }
            List<org.miau.particleeffects.animation.AnimationToken> tokens = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                try {
                    tokens.add(org.miau.particleeffects.animation.AnimationToken.parse(buf.readString(64)));
                } catch (RuntimeException e) {
                    return AnimationSet.EMPTY;
                }
            }
            try {
                return AnimationSet.of(tokens);
            } catch (RuntimeException e) {
                return AnimationSet.EMPTY;
            }
        }

        @Override
        public void encode(PacketByteBuf buf, AnimationSet value) {
            List<org.miau.particleeffects.animation.AnimationToken> tokens = value.tokens();
            buf.writeVarInt(tokens.size());
            for (org.miau.particleeffects.animation.AnimationToken token : tokens) {
                buf.writeString(token.toString());
            }
        }
    };

    static final PacketCodec<PacketByteBuf, MoveSpec> MOVE = new PacketCodec<>() {
        @Override
        public MoveSpec decode(PacketByteBuf buf) {
            DoubleWrapper d = new DoubleWrapper(
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
            int ticks = buf.readVarInt();
            Easing curve = Easings.byName(buf.readString(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            return new MoveSpec(d.asVec3d(), ticks, curve);
        }

        @Override
        public void encode(PacketByteBuf buf, MoveSpec value) {
            buf.writeDouble(value.delta().x);
            buf.writeDouble(value.delta().y);
            buf.writeDouble(value.delta().z);
            buf.writeVarInt(value.durationTicks());
            buf.writeString(Easings.nameOf(value.curve()));
        }
    };

    static final PacketCodec<PacketByteBuf, TextDisplayParams> TEXT = new PacketCodec<>() {
        @Override
        public TextDisplayParams decode(PacketByteBuf buf) {
            String text = buf.readString(4096);
            DoubleWrapper p = new DoubleWrapper(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float scale = buf.readFloat();
            int color = buf.readInt();
            ColorMode colorMode = ColorMode.parse(buf.readString(32));
            if (colorMode == null) {
                colorMode = ColorMode.SOLID;
            }
            List<Integer> gradientColors = new ArrayList<>();
            int gc = buf.readVarInt();
            for (int i = 0; i < gc; i++) {
                gradientColors.add(buf.readInt());
            }
            Orientation orientation = ORIENTATION.decode(buf);
            int duration = buf.readVarInt();
            int enter = buf.readVarInt();
            int exit = buf.readVarInt();
            int delay = buf.readVarInt();
            Easing curve = Easings.byName(buf.readString(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            AnimationSet entry = ANIMATION_SET.decode(buf);
            AnimationSet exitSet = ANIMATION_SET.decode(buf);
            FadeOption fade = FadeOption.parse(buf.readString(16));
            if (fade == null) {
                fade = FadeOption.NONE;
            }
            float spread = buf.readFloat();
            double density = buf.readDouble();
            MoveSpec move = buf.readBoolean() ? MOVE.decode(buf) : null;
            String id = buf.readBoolean() ? buf.readString(128) : null;
            try {
                return new TextDisplayParams(
                        text, new net.minecraft.util.math.Vec3d(p.x, p.y, p.z), scale, color,
                        colorMode, gradientColors,
                        orientation, duration, enter, exit, delay,
                        curve, entry, exitSet, fade, spread, density, move, id);
            } catch (RuntimeException e) {
                return fallbackText(text, p);
            }
        }

        private static TextDisplayParams fallbackText(String text, DoubleWrapper p) {
            return new TextDisplayParams(
                    "MiauParticleEffects", p.asVec3d(), 1f, 0xFFFFFFFF,
                    ColorMode.SOLID, List.of(),
                    new Orientation(0f, 0f, 0f), 100, 0, 20, 0,
                    Easings.EASE_OUT, AnimationSet.EMPTY, AnimationSet.EMPTY,
                    FadeOption.NONE, 3f, 0.1, null, null);
        }

        @Override
        public void encode(PacketByteBuf buf, TextDisplayParams value) {
            buf.writeString(value.text());
            buf.writeDouble(value.pos().x);
            buf.writeDouble(value.pos().y);
            buf.writeDouble(value.pos().z);
            buf.writeFloat(value.scale());
            buf.writeInt(value.colorArgb());
            buf.writeString(value.colorMode().id());
            buf.writeVarInt(value.gradientColors().size());
            for (int c : value.gradientColors()) {
                buf.writeInt(c);
            }
            ORIENTATION.encode(buf, value.orientation());
            buf.writeVarInt(value.durationTicks());
            buf.writeVarInt(value.enterTicks());
            buf.writeVarInt(value.exitTicks());
            buf.writeVarInt(value.delayTicks());
            buf.writeString(Easings.nameOf(value.curve()));
            ANIMATION_SET.encode(buf, value.entry());
            ANIMATION_SET.encode(buf, value.exit());
            buf.writeString(value.fade().id());
            buf.writeFloat(value.spread());
            buf.writeDouble(value.density());
            buf.writeBoolean(value.move() != null);
            if (value.move() != null) {
                MOVE.encode(buf, value.move());
            }
            buf.writeBoolean(value.id() != null);
            if (value.id() != null) {
                buf.writeString(value.id());
            }
        }
    };

    static final PacketCodec<PacketByteBuf, EffectDisplayParams> EFFECT = new PacketCodec<>() {
        @Override
        public EffectDisplayParams decode(PacketByteBuf buf) {
            EffectType type = EffectType.parse(buf.readString(32));
            DoubleWrapper p = new DoubleWrapper(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float size = buf.readFloat();
            float waveSpeed = buf.readFloat();
            int color = buf.readInt();
            ColorMode colorMode = ColorMode.parse(buf.readString(32));
            if (colorMode == null) {
                colorMode = ColorMode.SOLID;
            }
            List<Integer> gradientColors = new ArrayList<>();
            int gc = buf.readVarInt();
            for (int i = 0; i < gc; i++) {
                gradientColors.add(buf.readInt());
            }
            Orientation orientation = ORIENTATION.decode(buf);
            RotationSpec rotation = null;
            if (buf.readBoolean()) {
                try {
                    rotation = new RotationSpec(
                            new org.joml.Vector3f(
                                    buf.readFloat(), buf.readFloat(), buf.readFloat()),
                            buf.readFloat());
                } catch (RuntimeException e) {
                    rotation = null;
                }
            }
            int duration = buf.readVarInt();
            int enter = buf.readVarInt();
            int exit = buf.readVarInt();
            int delay = buf.readVarInt();
            Easing curve = Easings.byName(buf.readString(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            AnimationSet entry = ANIMATION_SET.decode(buf);
            AnimationSet exitSet = ANIMATION_SET.decode(buf);
            FadeOption fade = FadeOption.parse(buf.readString(16));
            if (fade == null) {
                fade = FadeOption.NONE;
            }
            MoveSpec move = buf.readBoolean() ? MOVE.decode(buf) : null;
            String id = buf.readBoolean() ? buf.readString(128) : null;
            if (type == null) {
                type = EffectType.CUBE;
            }
            try {
                return new EffectDisplayParams(
                        type, new net.minecraft.util.math.Vec3d(p.x, p.y, p.z), size, waveSpeed, color,
                        colorMode, gradientColors,
                        orientation, rotation, duration, enter, exit, delay,
                        curve, entry, exitSet, fade, move, id);
            } catch (RuntimeException e) {
                return new EffectDisplayParams(
                        EffectType.CUBE, p.asVec3d(), 2f, 0f, 0xFFFFFFFF,
                        ColorMode.SOLID, List.of(),
                        new Orientation(0f, 0f, 0f), null, 100, 0, 10, 0,
                        Easings.EASE_OUT, AnimationSet.EMPTY, AnimationSet.EMPTY,
                        FadeOption.NONE, null, null);
            }
        }

        @Override
        public void encode(PacketByteBuf buf, EffectDisplayParams value) {
            buf.writeString(value.type().id());
            buf.writeDouble(value.pos().x);
            buf.writeDouble(value.pos().y);
            buf.writeDouble(value.pos().z);
            buf.writeFloat(value.size());
            buf.writeFloat(value.waveSpeed());
            buf.writeInt(value.colorArgb());
            buf.writeString(value.colorMode().id());
            buf.writeVarInt(value.gradientColors().size());
            for (int c : value.gradientColors()) {
                buf.writeInt(c);
            }
            ORIENTATION.encode(buf, value.orientation());
            buf.writeBoolean(value.rotation() != null);
            if (value.rotation() != null) {
                buf.writeFloat(value.rotation().axis().x);
                buf.writeFloat(value.rotation().axis().y);
                buf.writeFloat(value.rotation().axis().z);
                buf.writeFloat(value.rotation().degreesPerTick());
            }
            buf.writeVarInt(value.durationTicks());
            buf.writeVarInt(value.enterTicks());
            buf.writeVarInt(value.exitTicks());
            buf.writeVarInt(value.delayTicks());
            buf.writeString(Easings.nameOf(value.curve()));
            ANIMATION_SET.encode(buf, value.entry());
            ANIMATION_SET.encode(buf, value.exit());
            buf.writeString(value.fade().id());
            buf.writeBoolean(value.move() != null);
            if (value.move() != null) {
                MOVE.encode(buf, value.move());
            }
            buf.writeBoolean(value.id() != null);
            if (value.id() != null) {
                buf.writeString(value.id());
            }
        }
    };

    private static final class DoubleWrapper {
        final double x;
        final double y;
        final double z;

        DoubleWrapper(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        net.minecraft.util.math.Vec3d asVec3d() {
            return new net.minecraft.util.math.Vec3d(x, y, z);
        }
    }
}