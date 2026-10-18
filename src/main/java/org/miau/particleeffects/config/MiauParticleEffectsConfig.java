package org.miau.particleeffects.config;

import java.util.ArrayList;
import java.util.List;

public final class MiauParticleEffectsConfig {

    /** 「音符盒触发特效」的默认参数（wave 于音符盒上方 2 格）。wave 只认 size/speed/id。 */
    public static final String DEFAULT_NOTE_TRIGGER_SPEC =
            "wave ~ ~2 ~ size=2.8 speed=8";

    public double density = 0.1;
    public boolean autoclear = false;
    public float defaultScale = 1.0F;

    /** 「音符盒触发特效」开关（/mpe noteblock triggereffect on|off）。 */
    public boolean noteTriggerEffect = false;
    /**
     * 旧版单条参数原文；仅用于把老配置迁移进 {@link #noteTriggerEffectSpecs}。
     * 迁移完成后会置空，因此用户清空全部特效应在重启后依然为空。
     */
    public String noteTriggerEffectSpec = DEFAULT_NOTE_TRIGGER_SPEC;
    /** 「音符盒触发特效」的多条参数原文，每条带一个稳定 id，供 remove 使用。 */
    public List<NoteTriggerSpec> noteTriggerEffectSpecs = new ArrayList<>();

    /** 一条触发特效配置：稳定 id + 参数原文（类型 相对坐标 [选项]）。 */
    public static final class NoteTriggerSpec {

        public String id = "";
        public String spec = "";

        public NoteTriggerSpec() {
        }

        public NoteTriggerSpec(String id, String spec) {
            this.id = id;
            this.spec = spec;
        }
    }
}
