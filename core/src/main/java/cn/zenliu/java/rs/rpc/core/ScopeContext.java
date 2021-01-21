package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-21
 */
public interface ScopeContext {

    void onDebug(Consumer<Logger> action);

    void onDebugElse(Consumer<Logger> onDebug, Consumer<Logger> orElse);

    void onDebugWithTimer(@Nullable Consumer<Logger> onDebugBeforeAction, @Nullable Consumer<Logger> onDebugAfterAction, Consumer<Logger> action);

    <T> T onDebugWithTimerReturns(@Nullable Consumer<Logger> onDebugBeforeAction, @Nullable Consumer<Logger> onDebugAfterAction, Function<Logger, T> action);

    String getName();

    boolean isRoute();

    java.util.Map<Integer, java.util.concurrent.ConcurrentSkipListSet<Remote>> getRemoteServices();

    java.util.List<String> getRemoteDomains();

    java.util.Map<Integer, Remote> getRemotes();

    java.util.List<String> getRemoteNames();

    java.util.concurrent.atomic.AtomicReference<java.util.Set<String>> getRoutes();

    java.util.Map<String, reactor.core.Disposable> getServers();

    java.util.List<String> getServices();

    java.util.Map<Integer, java.util.function.Function<Object[], cn.zenliu.java.rs.rpc.api.Result<Object>>> getHandlers();

    java.util.List<String> getHandlerSignatures();

    java.util.concurrent.atomic.AtomicBoolean getDebug();

    java.util.concurrent.atomic.AtomicReference<java.time.Duration> getTimeout();

    @Nullable Remote findRemoteService(String domain);

    Optional<Remote> findRemoteServiceOptional(String domain);

    void addOrUpdateRemote(Remote newRemote, Remote remote, boolean sync);

    int prepareRemoteName(String name);

    void updateRemoteService(Remote remote, Remote old);

    void calcRoutes();

    @Nullable
    Function<Object[], Result<Object>> findHandler(String handlerSignature);

    Optional<Function<Object[], Result<Object>>> findHandlerOptional(String handlerSignature);
}
