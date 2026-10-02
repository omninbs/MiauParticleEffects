package org.miau.particleeffects.protocol;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
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

    static final StreamCodec<FriendlyByteBuf, Orientation> ORIENTATION = new StreamCodec<>() {
        @Override
        public Orientation decode(FriendlyByteBuf buf) {
            return new Orientation(buf.readFloat(), buf.readFloat(), buf.readFloat());
        }

        @Override
        public void encode(FriendlyByteBuf buf, Orientation value) {
            buf.writeFloat(value.yaw());
            buf.writeFloat(value.pitch());
            buf.writeFloat(value.roll());
        }
    };

    static final StreamCodec<FriendlyByteBuf, AnimationSet> ANIMATION_SET = new StreamCodec<>() {
        @Override
        public AnimationSet decode(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            if (count <= 0) {
                return AnimationSet.EMPTY;
            }
            List<org.miau.particleeffects.animation.AnimationToken> tokens = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                try {
                    tokens.add(org.miau.particleeffects.animation.AnimationToken.parse(buf.readUtf(64)));
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
        public void encode(FriendlyByteBuf buf, AnimationSet value) {
            List<org.miau.particleeffects.animation.AnimationToken> tokens = value.tokens();
            buf.writeVarInt(tokens.size());
            for (org.miau.particleeffects.animation.AnimationToken token : tokens) {
                buf.writeUtf(token.toString());
            }
        }
    };

    static final StreamCodec<FriendlyByteBuf, MoveSpec> MOVE = new StreamCodec<>() {
        @Override
        public MoveSpec decode(FriendlyByteBuf buf) {
            DoubleWrapper d = new DoubleWrapper(
                    buf.readDouble(), buf.readDouble(), buf.readDouble());
            int ticks = buf.readVarInt();
            Easing curve = Easings.byName(buf.readUtf(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            return new MoveSpec(d.asVec3d(), ticks, curve);
        }

        @Override
        public void encode(FriendlyByteBuf buf, MoveSpec value) {
            buf.writeDouble(value.delta().x);
            buf.writeDouble(value.delta().y);
            buf.writeDouble(value.delta().z);
            buf.writeVarInt(value.durationTicks());
            buf.writeUtf(Easings.nameOf(value.curve()));
        }
    };

    static final StreamCodec<FriendlyByteBuf, TextDisplayParams> TEXT = new StreamCodec<>() {
        @Override
        public TextDisplayParams decode(FriendlyByteBuf buf) {
            String text = buf.readUtf(4096);
            DoubleWrapper p = new DoubleWrapper(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float scale = buf.readFloat();
            int color = buf.readInt();
            ColorMode colorMode = ColorMode.parse(buf.readUtf(32));
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
            Easing curve = Easings.byName(buf.readUtf(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            AnimationSet entry = ANIMATION_SET.decode(buf);
            AnimationSet exitSet = ANIMATION_SET.decode(buf);
            FadeOption fade = FadeOption.parse(buf.readUtf(16));
            if (fade == null) {
                fade = FadeOption.NONE;
            }
            float spread = buf.readFloat();
            double density = buf.readDouble();
            MoveSpec move = buf.readBoolean() ? MOVE.decode(buf) : null;
            String id = buf.readBoolean() ? buf.readUtf(128) : null;
            try {
                return new TextDisplayParams(
                        text, new net.minecraft.world.phys.Vec3(p.x, p.y, p.z), scale, color,
                        colorMode, gradientColors,
                        orientation, duration, enter, exit, delay,
                        curve, entry, exitSet, fade, spread, density, move, id, buf.readBoolean());
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
                    FadeOption.NONE, 3f, 0.1, null, null, false);
        }

        @Override
        public void encode(FriendlyByteBuf buf, TextDisplayParams value) {
            buf.writeUtf(value.text());
            buf.writeDouble(value.pos().x);
            buf.writeDouble(value.pos().y);
            buf.writeDouble(value.pos().z);
            buf.writeFloat(value.scale());
            buf.writeInt(value.colorArgb());
            buf.writeUtf(value.colorMode().id());
            buf.writeVarInt(value.gradientColors().size());
            for (int c : value.gradientColors()) {
                buf.writeInt(c);
            }
            ORIENTATION.encode(buf, value.orientation());
            buf.writeVarInt(value.durationTicks());
            buf.writeVarInt(value.enterTicks());
            buf.writeVarInt(value.exitTicks());
            buf.writeVarInt(value.delayTicks());
            buf.writeUtf(Easings.nameOf(value.curve()));
            ANIMATION_SET.encode(buf, value.entry());
            ANIMATION_SET.encode(buf, value.exit());
            buf.writeUtf(value.fade().id());
            buf.writeFloat(value.spread());
            buf.writeDouble(value.density());
            buf.writeBoolean(value.move() != null);
            if (value.move() != null) {
                MOVE.encode(buf, value.move());
            }
            buf.writeBoolean(value.id() != null);
            if (value.id() != null) {
                buf.writeUtf(value.id());
            }
            buf.writeBoolean(value.force());
        }
    };

    static final StreamCodec<FriendlyByteBuf, EffectDisplayParams> EFFECT = new StreamCodec<>() {
        @Override
        public EffectDisplayParams decode(FriendlyByteBuf buf) {
            EffectType type = EffectType.parse(buf.readUtf(32));
            DoubleWrapper p = new DoubleWrapper(buf.readDouble(), buf.readDouble(), buf.readDouble());
            float size = buf.readFloat();
            float waveSpeed = buf.readFloat();
            int color = buf.readInt();
            ColorMode colorMode = ColorMode.parse(buf.readUtf(32));
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
            Easing curve = Easings.byName(buf.readUtf(64));
            if (curve == null) {
                curve = Easings.EASE_OUT;
            }
            AnimationSet entry = ANIMATION_SET.decode(buf);
            AnimationSet exitSet = ANIMATION_SET.decode(buf);
            FadeOption fade = FadeOption.parse(buf.readUtf(16));
            if (fade == null) {
                fade = FadeOption.NONE;
            }
            MoveSpec move = buf.readBoolean() ? MOVE.decode(buf) : null;
            String id = buf.readBoolean() ? buf.readUtf(128) : null;
            if (type == null) {
                type = EffectType.CUBE;
            }
            try {
                return new EffectDisplayParams(
                        type, new net.minecraft.world.phys.Vec3(p.x, p.y, p.z), size, waveSpeed, color,
                        colorMode, gradientColors,
                        orientation, rotation, duration, enter, exit, delay,
                        curve, entry, exitSet, fade, move, id, buf.readBoolean());
            } catch (RuntimeException e) {
                return new EffectDisplayParams(
                        EffectType.CUBE, p.asVec3d(), 2f, 0f, 0xFFFFFFFF,
                        ColorMode.SOLID, List.of(),
                        new Orientation(0f, 0f, 0f), null, 100, 0, 10, 0,
                        Easings.EASE_OUT, AnimationSet.EMPTY, AnimationSet.EMPTY,
                        FadeOption.NONE, null, null, false);
            }
        }

        @Override
        public void encode(FriendlyByteBuf buf, EffectDisplayParams value) {
            buf.writeUtf(value.type().id());
            buf.writeDouble(value.pos().x);
            buf.writeDouble(value.pos().y);
            buf.writeDouble(value.pos().z);
            buf.writeFloat(value.size());
            buf.writeFloat(value.waveSpeed());
            buf.writeInt(value.colorArgb());
            buf.writeUtf(value.colorMode().id());
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
            buf.writeUtf(Easings.nameOf(value.curve()));
            ANIMATION_SET.encode(buf, value.entry());
            ANIMATION_SET.encode(buf, value.exit());
            buf.writeUtf(value.fade().id());
            buf.writeBoolean(value.move() != null);
            if (value.move() != null) {
                MOVE.encode(buf, value.move());
            }
            buf.writeBoolean(value.id() != null);
            if (value.id() != null) {
                buf.writeUtf(value.id());
            }
            buf.writeBoolean(value.force());
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

        net.minecraft.world.phys.Vec3 asVec3d() {
            return new net.minecraft.world.phys.Vec3(x, y, z);
        }
    }
}
