package org.miau.particleeffects.model;

/**
 * 单个字符（字/标点/空格）在歌词句内的时间表条目。
 *
 * <p>语义：</p>
 * <ul>
 *   <li>{@code startTick}：相对**所属段自己**的 {@code start} 字段的 tick 偏移，字在这一刻开始出现；</li>
 *   <li>{@code durationTicks}：这个字从「开始出现」到「开始退场」的时长（tick）。
 *       一个字持续得越长（长音），下一个字就越可能开启新的一句——断句判断在生成器侧完成。</li>
 * </ul>
 *
 * <p>多个字允许拥有相同的 {@code startTick}（同一 tick 一起出现），也允许时间区间重叠。</p>
 */
public record CharTiming(int startTick, int durationTicks) {

    public CharTiming {
        if (startTick < 0) {
            throw new IllegalArgumentException("字的 start 不能为负（单位 tick）");
        }
        if (durationTicks < 1) {
            throw new IllegalArgumentException("字的 duration 至少为 1 tick");
        }
    }

    /** 这个字开始退场的时刻（相对所属段 start）。 */
    public int endTick() {
        return startTick + durationTicks;
    }
}
