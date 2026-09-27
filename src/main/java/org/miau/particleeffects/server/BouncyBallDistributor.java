package org.miau.particleeffects.server;

import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 弹力球与服务端目标的分配器（服务端权威）。 *
 * 规则。 * 1. 目标。。空闲球数：每球一个【互不重复】的目标，多余目标留队等待；
 * 2. 目标。< 空闲球数：先让前 N 个球各取一个目标（仍不重复），
 *    剩余球按轮询复用本次目标（允许多球共跳同一音符盒）。 *
 * 任何情况下，只要“目标数 。球数”，就不会有两球共跳同一目标。 */
public final class BouncyBallDistributor {

    public record Assignment(int ballIndex, Vec3d target, int note) {
    }

    private final int ballCount;
    private final Deque<Integer> idleBalls = new ArrayDeque<>();
    private final Set<Integer> busy = new HashSet<>();
    private final List<Vec3d> pendingTargets = new ArrayList<>();
    private final List<Integer> pendingNotes = new ArrayList<>();

    /** 未分配目标队列上限：防止客户端全部离线、球长期不落地时目标无限堆积。*/
    private static final int MAX_PENDING = 64;

    public BouncyBallDistributor(int ballCount) {
        if (ballCount < 1) {
            throw new IllegalArgumentException("弹力球数量必须大于 0");
        }
        this.ballCount = ballCount;
        for (int i = 0; i < ballCount; i++) {
            idleBalls.add(i);
        }
    }

    public int ballCount() {
        return ballCount;
    }

    /**
     * 一个目标（激活的音符盒上表面中心）到达，尝试立即分配。     */
    public List<Assignment> pushTarget(Vec3d target, int note) {
        return pushTargets(List.of(target), List.of(note));
    }

    /**
     * 一批目标（同一服务。tick 内激活的多个音符盒）到达，合并后一次性分配，
     * 确保“多个音符盒同时激活”时弹力球能正确均匀分布到不同音符盒。     * 而不是被逐条分配拆散导致多球共跳同一目标。     */
    public List<Assignment> pushTargets(List<Vec3d> targets, List<Integer> notes) {
        if (targets.isEmpty()) {
            return List.of();
        }
        for (int i = 0; i < targets.size(); i++) {
            pendingTargets.add(targets.get(i));
            pendingNotes.add(notes.get(i));
        }
        if (pendingTargets.size() > MAX_PENDING) {
            // 丢弃最旧的目标，防止队列无限增长。
            int overflow = pendingTargets.size() - MAX_PENDING;
            pendingTargets.subList(0, overflow).clear();
            pendingNotes.subList(0, overflow).clear();
        }
        return dispatch();
    }

    /**
     * 某颗弹力球落地（客户端上报），释放后尝试分配排队目标。     */
    public List<Assignment> onBallLanded(int ballIndex) {
        if (!busy.remove(ballIndex)) {
            return List.of();
        }
        idleBalls.add(ballIndex);
        return dispatch();
    }

    public boolean isBusy(int ballIndex) {
        return busy.contains(ballIndex);
    }

    private List<Assignment> dispatch() {
        if (pendingTargets.isEmpty() || idleBalls.isEmpty()) {
            return List.of();
        }
        List<Assignment> out = new ArrayList<>();
        int uniqueCount = Math.min(pendingTargets.size(), idleBalls.size());

        List<Vec3d> usedTargets = new ArrayList<>(uniqueCount);
        List<Integer> usedNotes = new ArrayList<>(uniqueCount);
        for (int i = 0; i < uniqueCount; i++) {
            int ball = idleBalls.poll();
            Vec3d target = pendingTargets.get(i);
            int note = pendingNotes.get(i);
            usedTargets.add(target);
            usedNotes.add(note);
            busy.add(ball);
            out.add(new Assignment(ball, target, note));
        }
        pendingTargets.subList(0, uniqueCount).clear();
        pendingNotes.subList(0, uniqueCount).clear();

        int extra = idleBalls.size();
        for (int i = 0; i < extra; i++) {
            int ball = idleBalls.poll();
            Vec3d target = usedTargets.get(i % usedTargets.size());
            int note = usedNotes.get(i % usedNotes.size());
            busy.add(ball);
            out.add(new Assignment(ball, target, note));
        }
        return out;
    }
}