package org.miau.particleeffects.client.noteblock;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import org.miau.particleeffects.client.ClientClock;
import org.miau.particleeffects.client.particle.ColoredEndRodParticle;
import org.miau.particleeffects.model.ColorCodec;
import org.miau.particleeffects.model.HorizontalCurve;
import org.miau.particleeffects.protocol.MiauParticleEffectsPackets.NoteChainPayload;

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
 *
 * <p><b>时间锚点只用客户端游戏刻</b>（{@link ClientTickClock}），不读任何世界时间——
 * 否则 {@code /time set}、日夜冻结、服务端时间包覆盖、replay 重建世界都会让时间轴跳变，
 * 轻则瞬移、重则一步跳完整首歌判定「播完」而直接消失。</p>
 */
public final class NoteChainPlayer {

    /** 拖尾撒点间距（方块）：越小轨迹越连续，粒子数越多。 */
    private static final double TRAIL_SPACING = 0.25;
    /** 单帧拖尾撒点上限，防止极端位移时粒子数失控。 */
    private static final int MAX_TRAIL_PER_FRAME = 64;
    
    /** 球体：亮核 + 球面均匀分布的若干外壳点，构成《滚动天空》式的发光小球。 */
    private static final int HEAD_AGE = 2;
    private static final float HEAD_CORE_SCALE = 2.6f;
    private static final float HEAD_SHELL_SCALE = 1.15f;
    private static final double HEAD_SHELL_RADIUS = 0.13;
    private static final int HEAD_SHELL_POINTS = 5;
    /** 黄金角，用于球面均匀取点。 */
    private static final double GOLDEN_ANGLE = 2.399963229728653;

    /**
     * 粒子速度补偿系数。
     *
     * <p>粒子寿命 {@link #HEAD_AGE}=2 tick、摩擦 0.91，两 tick 的总位移是 {@code v*(1+0.91)}。
     * 传入「一个 tick 的位移 ÷ 1.91」，粒子就能在寿命结束时正好滑到下一 tick 的位置，
     * 而渲染时引擎会按 partialTick 在两 tick 之间插值 —— 这就是原版粒子连续运动的做法。</p>
     */
    private static final double PARTICLE_VELOCITY_SCALE = 1.0 / 1.91;
    /** 暂停/freeze 时补撒静态点的最小间隔（50ms ≈ 20Hz，与正常节奏一致）。 */
    private static final long STATIC_RESPAWN_NANOS = 50_000_000L;

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
    private final long restPhase;

    /**
     * 服务端广播的最新时间轴（tick，权威值）。
     *
     * <p>为什么由服务端驱动：客户端 tick 事件的频率**不随 {@code /tick rate} 变化**，
     * 客户端也拿不到可靠的服务端 tick 时间，所以任何「本地计时」在 {@code /tick rate}
     * 改变后都会仍以 20 tick/s 走。而服务端 tick 频率是权威的（音符盒触发特效就是靠它
     * 才正常变速的），因此链的进度由服务端每 tick 广播，客户端只做平滑跟随。</p>
     */
    private double serverTicks = Double.NaN;
    /** 平滑后的显示时间轴：前馈（按估算速率推进）+ 反馈（向服务端值收敛）。 */
    private double displayTicks = Double.NaN;
    /** 上一次收到进度样本的时间戳与数值，用于估算服务端 tick 速率。 */
    private long lastProgressNanos;
    private double lastProgressTicks = Double.NaN;
    /** 估算的服务端 tick 速率（tick/s），初值 20，靠样本间隔自适应。 */
    private float rateEstimate = 20f;
    /** 上一次渲染帧的时间戳。 */
    private long lastFrameNanos;
    /** 收不到服务端进度时的兜底时钟。 */
    private long lastFallbackTick = Long.MIN_VALUE;
    /** 上一次「撒点」时的显示时间轴，用来算本批粒子的速度补偿。 */
    private double lastEmitTicks = Double.NaN;
    /** 上一次「暂停/freeze 期间补撒静态点」的时间戳。 */
    private long lastStaticNanos;
    /** 上一次 tick 路径撒点的时间戳；用于判断客户端 tick 是否还在跑。 */
    private long lastTickNanos;
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

    /** 已经播放了多少 tick（用于重复下发防抖）。 */
    public long elapsedTicks() {
        return Double.isNaN(displayTicks) ? 0L : (long) displayTicks;
    }

    /**
     * 收到服务端广播的链进度（tick）。这是时间轴的**唯一权威来源**：
     * 服务端 tick 频率跟随 {@code /tick rate}，暂停/{@code /tick freeze} 时停止，回放时按录制重放。
     */
    public void onServerProgress(float elapsed) {
        long now = System.nanoTime();
        if (!Double.isNaN(lastProgressTicks) && lastProgressNanos != 0L) {
            double dt = (now - lastProgressNanos) * 1e-9;
            double dTicks = elapsed - lastProgressTicks;
            if (dt > 0.004 && dTicks > 0.0) {
                float measured = (float) (dTicks / dt);
                // 只在合理区间内采信，避免单个异常样本把速率带偏。
                if (measured > 0.05f && measured < 2000f) {
                    rateEstimate = rateEstimate * 0.6f + measured * 0.4f;
                }
            }
        }
        lastProgressTicks = elapsed;
        lastProgressNanos = now;
        if (Double.isNaN(serverTicks)) {
            // 首个样本：直接对齐，避免从 0 开始追赶。
            serverTicks = elapsed;
            displayTicks = elapsed;
        } else {
            serverTicks = elapsed;
        }
    }

    /**
     * 渲染帧侧：把显示时间轴向服务端值平滑推进（前馈 + 反馈），并在时间停止时补撒静态点。
     *
     * <p>前馈用估算出的服务端 tick 速率，保证匀速；反馈把累计误差拉回服务端权威值。
     * 这样即使客户端 tick 频率与 tick rate 不一致，画面速度也完全跟随服务端。</p>
     */
    public void frame(ClientLevel world, float deltaTicks) {
        if (removed) {
            return;
        }
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0L ? 0.0 : (now - lastFrameNanos) * 1e-9;
        lastFrameNanos = now;
        if (Double.isNaN(serverTicks)) {
            // 兜底：完全收不到服务端进度（服务端未装模组、或回放未重放该包）时，
            // 退回「客户端游戏刻」推进，至少让球能按本地节奏走完。
            long clock = ClientClock.ticks();
            if (Double.isNaN(displayTicks)) {
                displayTicks = 0.0;
                lastFallbackTick = clock;
                return;
            }
            long step = clock - lastFallbackTick;
            lastFallbackTick = clock;
            if (step > 0 && step < 20) {
                displayTicks += step;
            }
            return;
        }

        // 服务端进度是否还在更新：超时时间随估算速率自适应（低 tick rate 时包更稀疏）。
        double timeoutSeconds = Math.max(0.25, 3.0 / Math.max(0.05f, rateEstimate));
        boolean progressAlive = lastProgressNanos != 0L
                && (now - lastProgressNanos) * 1e-9 < timeoutSeconds;
        boolean timeRunning = deltaTicks > 0f && progressAlive;

        if (timeRunning && dt > 0.0 && dt < 0.5) {
            // 前馈：按服务端 tick 速率推进
            displayTicks += rateEstimate * dt;
            // 反馈：向权威值收敛（25% 每帧，约 3~4 帧内跟上）
            displayTicks += (serverTicks - displayTicks) * 0.25;
            // 允许最多落后 3 tick（网络抖动缓冲），不允许明显超前
            if (displayTicks < serverTicks - 3.0) {
                displayTicks = serverTicks - 3.0;
            }
            if (displayTicks > serverTicks + 1.0) {
                displayTicks = serverTicks + 1.0;
            }
        }

        if (removed) {
            return;
        }
        // 时间停止（暂停/freeze/断流）：补撒静态点，避免粒子过期后球消失。
        // 只有客户端 tick 也停了（单人 Esc，通常 tick 仍在跑）才需要补，避免和 tick 路径双撒。
        if (!timeRunning && now - lastTickNanos > 80_000_000L) {
            if (now - lastStaticNanos < STATIC_RESPAWN_NANOS) {
                return;
            }
            lastStaticNanos = now;
            renderAt(world, displayTicks, 0.0);
        }
    }

    /**
     * 每客户端 tick 撒一次粒子（粒子只有按 tick 撒才能当帧被引擎收进渲染组）。
     *
     * <p>这一批粒子的速度补偿按「本批跨越的服务端时间」计算：
     * {@code advance = displayTicks - lastEmitTicks}，因此 tick rate 再高，
     * 粒子也能在两 tick 寿命内滑到正确的下一位置。</p>
     */
    public void tick(ClientLevel world) {
        if (removed || Double.isNaN(displayTicks)) {
            return;
        }
        lastTickNanos = System.nanoTime();
        // linger < 0 表示常驻；否则最后一个音符结束后再停留 linger tick 自动移除。
        if (linger >= 0 && displayTicks >= totalTicks + linger) {
            removed = true;
            return;
        }
        double advance = Double.isNaN(lastEmitTicks) ? 0.0 : displayTicks - lastEmitTicks;
        if (advance < 0.0) {
            advance = 0.0;
        }
        lastEmitTicks = displayTicks;
        renderAt(world, displayTicks, advance);
    }

    /** 按给定时间（tick，含小数）求位置并撒点；{@code advance} 是本批跨越的服务端 tick 数。 */
    private void renderAt(ClientLevel world, double t, double advance) {
        Vec3 last = points[points.length - 1];
        if (totalTicks <= 0 || t >= totalTicks) {
            pos = last;
            restPos = last;
            spawnRestGlow(world, (long) t);
            return;
        }
        pos = positionAt(t);
        emitTrail(world, t);
        emitHead(world, t, advance);
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

    /** 二分定位当前所在的段：整首歌音符很多时避免每帧线性扫描。 */
    private int segmentAt(double t) {
        int lo = 0;
        int hi = delays.length - 1;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (t < startTimes[mid + 1]) {
                hi = mid;
            } else {
                lo = mid + 1;
            }
        }
        return lo;
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

    /**
     * 球体：亮核 + 球面外壳点。
     *
     * <p>粒子带**前进速度**：位移取「本批跨越的服务端时间」{@code advance}，
     * 除以粒子两 tick 的总衰减 {@code 1+0.91}，于是粒子在寿命结束时正好滑到
     * {@code positionAt(t + advance)}，引擎再按 partialTick 插值 —— 视觉连续。
     * 这样即使 {@code /tick rate} 把速度放大数倍，粒子也不会落后于球头。</p>
     */
    private void emitHead(ClientLevel world, double t, double advance) {
        int c = renderColor(t);
        // 单批位移限制在 8 tick 内，避免跳转/卡顿后粒子飞得过远。
        double step = Math.max(0.0, Math.min(advance, 8.0));
        Vec3 ahead = positionAt(Math.min(totalTicks, t + step));
        double vx = (ahead.x - pos.x) * PARTICLE_VELOCITY_SCALE;
        double vy = (ahead.y - pos.y) * PARTICLE_VELOCITY_SCALE;
        double vz = (ahead.z - pos.z) * PARTICLE_VELOCITY_SCALE;
        spawnHead(world, pos.x, pos.y, pos.z, vx, vy, vz, c, HEAD_CORE_SCALE, HEAD_AGE);
        for (int i = 0; i < HEAD_SHELL_POINTS; i++) {
            double y = 1.0 - 2.0 * (i + 0.5) / HEAD_SHELL_POINTS;
            double r = Math.sqrt(Math.max(0.0, 1.0 - y * y));
            double theta = i * GOLDEN_ANGLE;
            double ox = Math.cos(theta) * r * HEAD_SHELL_RADIUS;
            double oy = y * HEAD_SHELL_RADIUS;
            double oz = Math.sin(theta) * r * HEAD_SHELL_RADIUS;
            spawnHead(world, pos.x + ox, pos.y + oy, pos.z + oz,
                    vx, vy, vz, c, HEAD_SHELL_SCALE, HEAD_AGE);
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

    private void spawnHead(ClientLevel world, double x, double y, double z,
                           double vx, double vy, double vz, int c, float scale, int age) {
        if (rainbow) {
            ColoredEndRodParticle.spawnRainbow(world, x, y, z, vx, vy, vz, scale, age, 0f, true, force);
        } else {
            ColoredEndRodParticle.spawnHead(world, x, y, z, vx, vy, vz, c, scale, age, force);
        }
    }
}
