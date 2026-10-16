package org.miau.particleeffects.client.noteblock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;
import org.miau.particleeffects.util.WorldTime;

import java.util.List;

/**
 * 一条音符链的客户端播放器：本地按整条时间轴推进弹力球，与红石完全解耦。
 *
 * 点序列 P[0]=锚点，P[i+1]=锚点+音符偏移（落点为音符盒上表面中心）。
 * 第 i 段从 P[i] 飞向 P[i+1]，时长 = delay[i] tick；delay=0 按最小 1 tick 处理。
 * 弹力球在第 i 个音符发声的同一 tick 恰好落到 P[i+1]，随后在最后一个音符停留 linger tick 再消失。
 *
 * 位置由渲染帧驱动（约 60FPS）：{@link #tick} 只推进时间轴，{@link #frame} 用
 * 「已过 tick 数 + 帧间插值系数」重新求位置并生成粒子，使轨迹按帧连续、没有折线感。
 */
public final class NoteChainPlayer {

    /** 拖尾撒点间距（方块）：越小轨迹越连续，粒子数越多。 */
    private static final double TRAIL_SPACING = 0.25;
    /** 单帧拖尾撒点上限，防止极端位移时粒子数失控。 */
    private static final int MAX_TRAIL_PER_FRAME = 64;
    /** startTick 与客户端世界时间的合理偏差上限；超出则改用“收包时刻为 0 点”，补偿网络延迟。 */
    private static final long MAX_CLOCK_SKEW = 6000L;

    /** 球体：一个亮核 + 球面均匀分布的若干外壳点，构成《滚动天空》式的发光小球。 */
    private static final int HEAD_AGE = 2;
    private static final float HEAD_CORE_SCALE = 2.6f;
    private static final float HEAD_SHELL_SCALE = 1.15f;
    private static final double HEAD_SHELL_RADIUS = 0.13;
    private static final int HEAD_SHELL_POINTS = 5;
    /** 黄金角，用于球面均匀取点。 */
    private static final double GOLDEN_ANGLE = 2.399963229728653;

    /** 彗尾：越靠尾部越小，形成锥形彗尾。 */
    private static final float TRAIL_SCALE_HEAD = 0.95f;
    private static final float TRAIL_SCALE_TAIL = 0.35f;

    private final String id;
    private final Vec3[] points;
    private final int[] delays;
    private final long[] startTimes;
    /** 每一段的顶点高度：由设定上限 H 结合该段的水平距离与间隔动态推导。 */
    private final double[] apexes;
    private final long totalTicks;
    private final int trail;
    private final HorizontalCurve curve;
    private final float height;
    private final boolean force;
    private final int color;
    private final List<Integer> gradient;
    private final int linger;
    private final boolean rainbow;
    private final long startTick;
    private final long restPhase;

    private long receivedAt;
    private boolean clockInitialised;
    private long elapsedTicks;
    private Vec3 pos;
    private Vec3 restPos;
    /** 上一次撒拖尾的时间参数与位置；用于按弧长（而不是按帧）决定撒点密度。 */
    private double lastEmitT = -1;
    private Vec3 lastEmitPos;
    private boolean removed;

    public NoteChainPlayer(NoteChainPayload payload) {
        this.id = payload.id();
        this.trail = Math.max(2, Math.min(256, (int) payload.trail()));
        HorizontalCurve parsed = HorizontalCurve.parse(payload.curve());
        this.curve = parsed != null ? parsed : HorizontalCurve.ARC;
        this.height = payload.height();
        this.force = payload.force();
        this.color = payload.color();
        this.gradient = List.copyOf(payload.gradientColors());
        this.linger = payload.linger();
        this.rainbow = gradient.isEmpty() && color == NoteChainPayload.RAINBOW_COLOR;
        this.startTick = payload.startTick();
        this.restPhase = Math.abs(id.hashCode());

        List<NoteChainPayload.Note> notes = payload.notes();
        int n = notes.size();
        this.points = new Vec3[n + 1];
        this.delays = new int[n];
        this.startTimes = new long[n + 1];
        this.apexes = new double[n];
        // P[0] 为锚点方块的上表面中心（球从命令方块上方出发），
        // 每个音符落点为「音符盒坐标 + 上表面中心偏移」，两者基准一致。
        this.points[0] = new Vec3(payload.ax() + 0.5, payload.ay() + 1.0, payload.az() + 0.5);
        long acc = 0;
        for (int i = 0; i < n; i++) {
            NoteChainPayload.Note note = notes.get(i);
            this.points[i + 1] = new Vec3(
                    payload.ax() + note.ox() + 0.5,
                    payload.ay() + note.oy() + 1.0,
                    payload.az() + note.oz() + 0.5);
            this.delays[i] = Math.max(1, note.delay());
            acc += this.delays[i];
            this.startTimes[i + 1] = acc;
            this.apexes[i] = apexFor(this.points[i], this.points[i + 1], this.delays[i]);
        }
        this.totalTicks = acc;
        this.pos = this.points[0];
        this.restPos = this.pos;
    }

    public String id() {
        return id;
    }

    public boolean isRemoved() {
        return removed;
    }

    /** 只推进时间轴与到期判定；位置与粒子交给 {@link #frame}。 */
    public void tick(ClientLevel world) {
        long now = WorldTime.ticks(world);
        if (!clockInitialised) {
            receivedAt = now;
            clockInitialised = true;
        }
        // 时间基准优先用 startTick 对齐服务端；偏差过大（跨世界/时钟不可比）时退化为“收包时刻为 0”。
        long raw = now - startTick;
        long elapsed = (raw >= 0 && raw <= MAX_CLOCK_SKEW) ? raw : (now - receivedAt);
        this.elapsedTicks = Math.max(0, elapsed);

        // linger < 0 表示常驻；否则最后一个音符结束后再停留 linger tick 自动移除。
        if (linger >= 0 && this.elapsedTicks >= totalTicks + linger) {
            removed = true;
        }
    }

    /** 渲染帧推进：按「tick + 插值系数」求精确位置，并沿曲线撒拖尾与球体。 */
    public void frame(ClientLevel world, float partialTick) {
        if (removed) {
            return;
        }
        double t = elapsedTicks + Math.max(0f, partialTick);
        Vec3 last = points[points.length - 1];
        if (totalTicks <= 0 || t >= totalTicks) {
            pos = last;
            restPos = last;
            spawnRestGlow(world, elapsedTicks);
            return;
        }
        pos = positionAt(t);
        emitTrail(world, t);
        emitHead(world, t);
    }

    // ------------------------------------------------------------------ 轨迹

    /**
     * 动态高度：设定值 H 只是上限，实际顶点按「水平距离 + 触发间隔」在 [H-2, H] 之间取值。
     * 距离越远、间隔越短（越急促）→ 越接近 H；距离越近、间隔越长 → 越接近 H-2。
     * H <= 2 时不再变化，顶点恒等于 H。
     */
    private double apexFor(Vec3 start, Vec3 target, int delayTicks) {
        if (height <= 2f) {
            return height;
        }
        double horizontal = Math.hypot(target.x - start.x, target.z - start.z);
        double reference = Math.max(4.0, 2.0 * height);
        double distanceFactor = Math.min(1.0, horizontal / reference);
        double delayFactor = Math.min(1.0, Math.max(0.0, delayTicks / 20.0));
        double factor = distanceFactor * (0.75 + 0.25 * (1.0 - delayFactor));
        double lowest = height - 2.0;
        return lowest + (height - lowest) * Math.min(1.0, factor);
    }

    private int segmentAt(double t) {
        for (int i = 0; i < delays.length; i++) {
            if (t < startTimes[i + 1]) {
                return i;
            }
        }
        return delays.length - 1;
    }

    /** 按段内参数 u∈[0,1] 求曲线上一点：水平直线插值，竖直按 curve 起伏。 */
    private Vec3 pointAt(int segment, double u) {
        Vec3 a = points[segment];
        Vec3 b = points[segment + 1];
        double x = a.x + (b.x - a.x) * u;
        double z = a.z + (b.z - a.z) * u;
        double yBase = a.y + (b.y - a.y) * u;
        double apex = apexes[segment];
        double y = switch (curve) {
            case LINE -> yBase;
            case ARC -> yBase + 4.0 * apex * u * (1.0 - u);
            case SINE -> yBase + apex * Math.sin(Math.PI * u);
        };
        double floor = Math.min(a.y, b.y);
        if (y < floor) {
            y = floor;
        }
        return new Vec3(x, y, z);
    }

    /** 按全局时间参数 t（单位 tick，可为小数）求位置。 */
    private Vec3 positionAt(double t) {
        if (totalTicks <= 0 || t <= 0) {
            return points[0];
        }
        if (t >= totalTicks) {
            return points[points.length - 1];
        }
        int segment = segmentAt(t);
        double u = (t - startTimes[segment]) / (double) delays[segment];
        return pointAt(segment, u);
    }

    // ------------------------------------------------------------------ 粒子

    /** 沿本帧走过的曲线段按固定弧长间距补点，保证轨迹由曲线采样构成而不是折线。 */
    private void emitTrail(ClientLevel world, double t) {
        if (lastEmitPos == null) {
            lastEmitPos = pos;
            lastEmitT = t;
            return;
        }
        double distance = pos.distanceTo(lastEmitPos);
        if (distance < TRAIL_SPACING) {
            return;
        }
        int steps = (int) Math.min(MAX_TRAIL_PER_FRAME,
                Math.max(1, Math.ceil(distance / TRAIL_SPACING)));
        int c = renderColor(t);
        double tailSpan = Math.max(1.0, trail);
        for (int i = 1; i <= steps; i++) {
            double k = (i - 0.5) / steps;
            double tt = lastEmitT + (t - lastEmitT) * k;
            Vec3 q = positionAt(tt);
            // age：0=贴近头部，1=尾端；据此让彗尾由粗到细。
            double age = Math.min(1.0, (t - tt) / tailSpan);
            float scale = (float) (TRAIL_SCALE_HEAD + (TRAIL_SCALE_TAIL - TRAIL_SCALE_HEAD) * age);
            spawnTrail(world, q.x, q.y, q.z, c, trail, scale);
        }
        lastEmitT = t;
        lastEmitPos = pos;
    }

    /** 球体：亮核 + 球面外壳点。每帧重撒，位置由帧间插值精确给出，因此在渲染帧上连续移动。 */
    private void emitHead(ClientLevel world, double t) {
        int c = renderColor(t);
        spawnHead(world, pos.x, pos.y, pos.z, c, HEAD_CORE_SCALE, HEAD_AGE);
        for (int i = 0; i < HEAD_SHELL_POINTS; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / HEAD_SHELL_POINTS;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = i * GOLDEN_ANGLE;
            double ox = Math.cos(theta) * r * HEAD_SHELL_RADIUS;
            double oy = y * HEAD_SHELL_RADIUS;
            double oz = Math.sin(theta) * r * HEAD_SHELL_RADIUS;
            spawnHead(world, pos.x + ox, pos.y + oy, pos.z + oz, c, HEAD_SHELL_SCALE, HEAD_AGE);
        }
    }

    private void spawnRestGlow(ClientLevel world, long elapsed) {
        // 隔帧撒 2 个细小静置光点，成本极低，停留期也能看清球的位置；按 id 错开相位避免多链同闪。
        if (((elapsed + restPhase) & 1L) != 0L) {
            return;
        }
        int c = renderColor(elapsed);
        for (int k = 0; k < 2; k++) {
            double ox = (world.getRandom().nextFloat() - 0.5f) * 0.6;
            double oz = (world.getRandom().nextFloat() - 0.5f) * 0.6;
            double oy = world.getRandom().nextFloat() * 0.3;
            if (rainbow) {
                ColoredEndRodParticle.spawnRainbow(world, restPos.x + ox, restPos.y + oy, restPos.z + oz,
                        0, 0, 0, 0.5f, 10, 0f, false, force);
            } else {
                ColoredEndRodParticle.spawnStatic(world, restPos.x + ox, restPos.y + oy, restPos.z + oz,
                        c, 0.5f, 10, force);
            }
        }
    }

    /** 当前颜色：有渐变时按链进度采样，否则用固有色（或彩虹）。 */
    private int renderColor(double t) {
        if (!gradient.isEmpty()) {
            float k = totalTicks <= 0 ? 1f : (float) Math.min(1.0, t / totalTicks);
            return ColorCodec.sampleGradient(gradient, k);
        }
        return color;
    }

    private void spawnTrail(ClientLevel world, double x, double y, double z, int c, int age, float scale) {
        if (rainbow) {
            ColoredEndRodParticle.spawnRainbow(world, x, y, z, 0, 0, 0, scale, age, 0f, false, force);
        } else {
            ColoredEndRodParticle.spawnMoving(world, x, y, z, 0, 0, 0, c, scale, age, force);
        }
    }

    private void spawnHead(ClientLevel world, double x, double y, double z, int c, float scale, int age) {
        if (rainbow) {
            ColoredEndRodParticle.spawnRainbow(world, x, y, z, 0, 0, 0, scale, age, 0f, true, force);
        } else {
            ColoredEndRodParticle.spawnHead(world, x, y, z, 0, 0, 0, c, scale, age, force);
        }
    }
}
