package cn.zenliu.java.rs.rpc.core;

import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

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

    boolean isRoute();


    void onDebug(Consumer<Logger> action);

    void withLog(Consumer<Logger> action);

    void onDebugElse(Consumer<Logger> onDebug, Consumer<Logger> orElse);

    void onDebugWithTimer(@Nullable Consumer<Logger> onDebugBeforeAction, @Nullable Consumer<Logger> onDebugAfterAction, Consumer<Logger> action);

    <T> T onDebugWithTimerReturns(@Nullable Consumer<Logger> onDebugBeforeAction, @Nullable Consumer<Logger> onDebugAfterAction, Function<Logger, T> action);


}
