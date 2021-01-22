package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Hold all Context data in a Scope
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-20
 */
abstract class ScopeContextImpl implements ContextScope, ContextServers, ContextServices {
    protected static final Logger log = org.slf4j.LoggerFactory.getLogger("cn.zenliu.java.rs.rpc.core.ScopeContext");

    /**
     * Scope Name
     */
    @Getter final String name;
    /**
     * Scope route ability
     */
    @Getter final boolean route;


    protected ScopeContextImpl(String name, boolean route) {
        this.name = name;
        this.route = route;
    }

    @Override
    public void withLog(Consumer<Logger> action) {
        action.accept(log);
    }

    @Override
    public void onDebug(Consumer<Logger> onDebug) {
        if (debug.get()) {
            if (onDebug != null) onDebug.accept(log);
        }
    }

    @Override
    public void onDebugElse(Consumer<Logger> onDebug, Consumer<Logger> orElse) {
        if (debug.get()) {
            if (onDebug != null) onDebug.accept(log);
        } else {
            if (orElse != null) orElse.accept(log);
        }
    }

    @Override
    public void onDebugWithTimer(Consumer<Logger> onDebugBefore, Consumer<Logger> onDebugAfter, Consumer<Logger> action) {
        if (debug.get()) {
            long ts = System.nanoTime();
            try {
                if (onDebugBefore != null) onDebugBefore.accept(log);
                action.accept(log);
                if (onDebugAfter != null) onDebugAfter.accept(log);
            } finally {
                log.debug("Total cost {} μs", (System.nanoTime() - ts) / 1000.0);
            }
        } else {
            action.accept(log);
        }
    }

    @Override
    public <T> T onDebugWithTimerReturns(@Nullable Consumer<Logger> onDebugBeforeAction, @Nullable Consumer<Logger> onDebugAfterAction, Function<Logger, T> action) {
        if (debug.get()) {
            long ts = System.nanoTime();
            try {
                if (onDebugBeforeAction != null) onDebugBeforeAction.accept(log);
                return action.apply(log);
            } finally {
                if (onDebugAfterAction != null) onDebugAfterAction.accept(log);
                log.debug("Total cost {} μs", (System.nanoTime() - ts) / 1000.0);
            }
        } else {
            return action.apply(log);
        }
    }


    @Getter final AtomicReference<Set<String>> routes = new AtomicReference<>(new HashSet<>());


    /**
     * local server both client and server
     */
    @Getter final Map<String, Disposable> servers = new ConcurrentHashMap<>();
    /**
     * local registered service domain
     */
    @Getter final UniqueList services = UniqueList.of(new CopyOnWriteArrayList<>());
    /**
     * local registered handler
     */
    @Getter final Map<Integer, Function<Object[], Result<Object>>> handlers = new ConcurrentSkipListMap<>();
    @Getter final UniqueList signs = UniqueList.of(new CopyOnWriteArrayList<>());



    /**
     * remotes domain registry
     */
    @Getter final Map<Integer, ConcurrentSkipListSet<Remote>> remoteServices = new ConcurrentSkipListMap<>();

    /**
     * store all Service Domain
     */
    @Getter final UniqueList domains = UniqueList.of(new CopyOnWriteArrayList<>());
    /**
     * store remote RSocket
     */
    @Getter final Map<Integer, Remote> remotes = new ConcurrentSkipListMap<>();
    /**
     * store remote names
     */
    @Getter final UniqueList remoteNames = UniqueList.of(new CopyOnWriteArrayList<>());




    @Getter final AtomicBoolean debug = new AtomicBoolean(false);
    @Getter final AtomicBoolean trace = new AtomicBoolean(false);
    @Getter final AtomicReference<Duration> timeout = new AtomicReference<>(Duration.ofSeconds(2));

    protected void purify() {
        services.clear();
        handlers.clear();
        signs.clear();

        servers.forEach((k, v) -> {
            if (!v.isDisposed()) v.dispose();
        });
        servers.clear();
        remotes.forEach((k, v) -> {
            if (!v.socket.isDisposed()) v.socket.dispose();
        });
        remotes.clear();
        remoteNames.clear();
        domains.clear();
        remoteServices.clear();
        routes.get().clear();
    }

    protected String dump() {
        return String.valueOf(domains);
    }
}
