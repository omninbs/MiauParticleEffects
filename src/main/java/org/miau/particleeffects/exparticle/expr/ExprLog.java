package org.miau.particleeffects.exparticle.expr;

import java.io.PrintStream;
import java.util.function.Consumer;

/**
 * 表达式引擎的错误输出出口。
 *
 * <p>引擎本身位于 main 源集、不依赖 Minecraft；客户端可注册一个消费者把错误显示到聊天栏，
 * 未注册时退回标准错误流。</p>
 */
public final class ExprLog {

    private static volatile Consumer<Throwable> sink;

    private ExprLog() {
    }

    /** 由客户端在初始化时注册聊天栏输出；传 null 可恢复默认行为。 */
    public static void setSink(Consumer<Throwable> consumer) {
        sink = consumer;
    }

    public static void error(Throwable throwable) {
        Consumer<Throwable> current = sink;
        if (current != null) {
            try {
                current.accept(throwable);
                return;
            } catch (RuntimeException ignored) {
                // 输出出口自身出错时退回标准错误流
            }
        }
        PrintStream out = System.err;
        out.println("[MiauParticleEffects/ExParticle] " + throwable);
        throwable.printStackTrace(out);
    }

    /** 把异常信息压成单行文本（聊天栏显示用）。 */
    public static String message(Throwable throwable) {
        String detail = throwable.getMessage();
        return detail == null || detail.isBlank()
                ? throwable.getClass().getSimpleName()
                : throwable.getClass().getSimpleName() + ": " + detail;
    }
}
