package org.miau.particleeffects.config;

public final class MiauParticleEffectsConfig {

    /** 「音符盒触发特效」的默认参数（wave 于音符盒上方 2 格，青色）。 */
    public static final String DEFAULT_NOTE_TRIGGER_SPEC =
            "wave ~ ~2 ~ duration=4 speed=8 enter=3 exit=0 size=2.8 color=#11FFFF";

    public double density = 0.1;
    public boolean autoclear = false;
    public float defaultScale = 1.0F;

    /** 「音符盒触发特效」开关（/mpe noteblock triggereffect on|off）。 */
    public boolean noteTriggerEffect = false;
    /** 「音符盒触发特效」参数原文（/mpe noteblock triggereffect effect 之后的全部内容）。 */
    public String noteTriggerEffectSpec = DEFAULT_NOTE_TRIGGER_SPEC;
}
