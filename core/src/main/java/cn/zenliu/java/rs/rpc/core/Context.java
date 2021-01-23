package cn.zenliu.java.rs.rpc.core;

import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface Context {
    AtomicBoolean getDebug();

    AtomicBoolean getTrace();

    AtomicReference<Duration> getTimeout();

    String getName();

    default @Nullable String getNameOnTrace() {
        if (getTrace().get() || getDebug().get()) return getName();
        return null;
    }

    boolean isRoute();


    void onDebug(String template, Object... args);

    void info(String template, Object... args);

    void debug(String template, Object... args);

    void warn(String template, Object... args);

    void error(String template, Object... args);

    @FunctionalInterface
    interface WrapDebug {
        void debug(String template, Object... args);
    }

    void onDebugElse(Consumer<WrapDebug> onDebug, Consumer<WrapDebug> orElse);

    void onDebugWithTimer(@Nullable Consumer<WrapDebug> onDebugBeforeAction, @Nullable Consumer<WrapDebug> onDebugAfterAction, Consumer<Context> action);

    <T> T onDebugWithTimerReturns(@Nullable Consumer<WrapDebug> onDebugBeforeAction, @Nullable Consumer<WrapDebug> onDebugAfterAction, Function<Context, T> action);


}
