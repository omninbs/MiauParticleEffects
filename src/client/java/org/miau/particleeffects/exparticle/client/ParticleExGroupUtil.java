package org.miau.particleeffects.exparticle.client;

import net.minecraft.client.particle.Particle;
import org.miau.particleeffects.exparticle.expr.ExpressionUtil;
import org.miau.particleeffects.exparticle.expr.IExecutable;
import org.miau.particleeffects.exparticle.expr.ParticleStruct;
import org.miau.particleeffects.mixin.client.ParticleAccessor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 粒子分组管理（移植自 ExParticle 的 GroupUtil）。组名以 {@code |} 分隔，可把同一粒子加入多个组。
 */
public final class ParticleExGroupUtil {

    private static final Map<String, List<Particle>> GROUPS = new HashMap<>();

    private ParticleExGroupUtil() {
    }

    public static void add(String group, Particle particle) {
        if (group == null || group.equals("null")) {
            return;
        }
        for (String name : group.split("\\|")) {
            GROUPS.computeIfAbsent(name, k -> new ArrayList<>()).add(particle);
        }
    }

    /** 按组移除粒子：expression 非空时按表达式条件筛选（非 0 则移除）。 */
    public static void remove(String group, String expression, double cx, double cy, double cz) {
        if (group == null || group.equals("null")) {
            return;
        }
        for (String name : group.split("\\|")) {
            List<Particle> particles = GROUPS.get(name);
            if (particles == null) {
                continue;
            }
            if (expression != null && !expression.equals("null")) {
                IExecutable exe = ExpressionUtil.prase(expression);
                ParticleStruct data = exe.getData();
                for (Particle particle : particles) {
                    if (!particle.isAlive()) {
                        continue;
                    }
                    ParticleAccessor accessor = (ParticleAccessor) particle;
                    double dx = accessor.exGetX() - cx;
                    double dy = accessor.exGetY() - cy;
                    double dz = accessor.exGetZ() - cz;
                    data.x = dx;
                    data.y = dy;
                    data.z = dz;
                    data.s1 = Math.atan2(dz, dx);
                    data.s2 = Math.atan2(dy, Math.hypot(dx, dz));
                    data.dis = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    data.age = accessor.exGetAge();
                    if (exe.invoke() != 0) {
                        particle.remove();
                    }
                }
                particles.removeIf(particle -> !particle.isAlive());
            } else {
                for (Particle particle : particles) {
                    particle.remove();
                }
                particles.clear();
            }
        }
    }

    public static List<Particle> get(String group) {
        if (group == null || group.equals("null")) {
            return Collections.emptyList();
        }
        List<Particle> result = new ArrayList<>();
        for (String name : group.split("\\|")) {
            List<Particle> particles = GROUPS.get(name);
            if (particles != null) {
                result.addAll(particles);
            }
        }
        return result;
    }

    public static void clear() {
        for (List<Particle> particles : GROUPS.values()) {
            particles.clear();
        }
        GROUPS.clear();
    }
}
