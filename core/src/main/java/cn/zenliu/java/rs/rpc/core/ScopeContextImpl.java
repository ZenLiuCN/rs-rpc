package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import org.slf4j.Logger;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.*;
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
abstract class ScopeContextImpl implements ScopeContext {
    protected static final Logger log = org.slf4j.LoggerFactory.getLogger("cn.zenliu.java.rs.rpc.core.ScopeContext");
    static char ROUTE_MARK = '?';
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


    //region routes Section: all service domain supported include in remotes if with route supported.
    @Getter final AtomicReference<Set<String>> routes = new AtomicReference<>(new HashSet<>());

    @Override
    public void calcRoutes() {
        onDebug(log -> log.debug("[{}] before calc routes {}", name, routes.get()));
        Set<String> routes = new HashSet<>(services);
        if (route) {
            onDebug(log -> log.debug("[{}] before calc routes {} with routeing", name, routes));
            routes.addAll(Seq.seq(remoteDomains).map(x -> x.endsWith(ROUTE_MARK + "") ? x : (x + ROUTE_MARK)).toSet());
            //routes.addAll(remoteDomains);
            onDebug(log -> log.debug("[{}] after calc routes {} with routeing", name, routes));
        }
        this.routes.set(routes);
        onDebug(log -> log.debug("[{}] after calc routes {}", name, this.routes.get()));
    }
    //endregion


    //region Local info section
    /**
     * local server both client and server
     */
    @Getter final Map<String, Disposable> servers = new ConcurrentHashMap<>();
    /**
     * local registered service domain
     */
    @Getter final List<String> services = new CopyOnWriteArrayList<>();
    /**
     * local registered handler
     */
    @Getter final Map<Integer, Function<Object[], Result<Object>>> handlers = new ConcurrentSkipListMap<>();
    /**
     * local registered handler domain
     */
    @Getter final List<String> handlerSignatures = new CopyOnWriteArrayList<>();

    protected int prepareHandlerSignature(String handlerSignature) {
        if (handlerSignatures.contains(handlerSignature)) {
            return -1;
        }
        handlerSignatures.add(handlerSignature);
        return handlerSignatures.size() - 1;
    }

    protected int findHandlerSignature(String handlerSignature) {
        return handlerSignatures.indexOf(handlerSignature);
    }

    protected void addHandler(String handlerSignature, Function<Object[], Result<Object>> handler) {
        onDebug(log -> log.debug("[{}] before register handler: \nsign: {},\nregistry: {}{} => {}", name, handlerSignature, handlerSignatures, handlers, services));
        final int index = prepareHandlerSignature(handlerSignature);
        if (index == -1) throw new IllegalStateException("a handler '" + handlerSignature + "' already exists! ");
        handlers.put(index, handler);
        onDebug(log -> log.debug("[{}] after register handler: \nsign: {}\nregistry: {}{} => {}", name, handlerSignature, handlerSignatures, handlers, services));
    }

    @Override
    public @Nullable
    Function<Object[], Result<Object>> findHandler(String handlerSignature) {
        final int index = findHandlerSignature(handlerSignature);
        if (index == -1) return null;
        return handlers.get(index);
    }

    @Override
    public Optional<Function<Object[], Result<Object>>> findHandlerOptional(String handlerSignature) {
        return Optional.ofNullable(findHandler(handlerSignature));

    }

    protected int prepareService(String service) {
        if (services.contains(service)) {
            return -1;
        }
        services.add(service);
        return services.size() - 1;
    }

    protected int findService(String service) {
        return services.indexOf(service);
    }

    protected boolean addService(String service) {
        onDebug(log -> log.debug("[{}] before to register service {} into {}", name, service, services));
        if (services.contains(service)) {
            log.error("[{}] error to register a exists service {} into {}", name, service, services);
            return false;
        }

        try {
            return services.add(service);
        } finally {
            onDebug(log -> log.debug("[{}] after to register a  service {} into {}", name, service, services));
        }
    }

    protected void addServer(Disposable server, String name) {
        servers.put(name, server);
    }
    //endregion
//region Remotes info section
    /**
     * remotes domain registry
     */
    @Getter final Map<Integer, ConcurrentSkipListSet<Remote>> remoteServices = new ConcurrentSkipListMap<>();

    /**
     * store all Service Domain
     */
    @Getter final List<String> remoteDomains = new CopyOnWriteArrayList<>();
    /**
     * store remote RSocket
     */
    @Getter final Map<Integer, Remote> remotes = new ConcurrentSkipListMap<>();
    /**
     * store remote names
     */
    @Getter final List<String> remoteNames = new CopyOnWriteArrayList<>();

    @Override
    public @Nullable Remote findRemoteService(String domain) {
        onDebug(log -> log.debug("[{}] before findRouteDomain {} in {}", name, domain, remoteDomains));
        int index = remoteDomains.indexOf(domain);
        if (index == -1 && !route) {
            onDebug(log -> log.debug("[{}] find no domain {} in {} with no routeing enabled.", name, domain, remoteDomains));
            return null;
        } else if (index == -1) {
            onDebug(log -> log.debug("[{}] try find domain {} in {} with routeing", name, domain + ROUTE_MARK, remoteDomains));
            index = remoteDomains.indexOf(domain + ROUTE_MARK);
        }
        if (index == -1) {
            onDebug(log -> log.debug("[{}] find no domain {} in {} with routeing", name, domain + ROUTE_MARK, remoteDomains));
            return null;
        }
        final ConcurrentSkipListSet<Remote> remotes = remoteServices.get(index);
        if (remotes.isEmpty()) {
            onDebug(log -> log.debug("[{}] find domain {} in {} with routeing, but found no Remote exists in {}!", name, domain + ROUTE_MARK, remoteDomains, remoteServices));
            return null;
        }
        return remotes.first();
    }

    @Override
    public Optional<Remote> findRemoteServiceOptional(String domain) {
        return Optional.ofNullable(findRemoteService(domain));
    }

    public int prepareRemoteName(String name) {
        if (remoteNames.contains(name)) {
            return -1;
        }
        remoteNames.add(name);
        return remoteNames.size() - 1;
    }

    protected int findRemoteName(String name) {
        return remoteNames.indexOf(name);
    }

    public void updateRemoteService(Remote remote, Remote old) {
        onDebug(log -> log.debug("[{}] before update remote service: {} to {} \n {} {}", name, remote, old, remoteDomains, remoteServices));
        if (old != null) {
            old.service.forEach(v -> removeRemoteService(v, old));
        }
        onDebug(log -> log.debug("[{}] after remove old when update remote service: {} to {} \n {} {}", name, remote, old, remoteDomains, remoteServices));
        for (String domain : remote.service) {
            addOrUpdateRemoteService(domain, remote);
        }
        onDebug(log -> log.debug("[{}] after update remote service: {} to {} \n {} {}", name, remote, old, remoteDomains, remoteServices));
        calcRoutes();
    }

    protected void addOrUpdateRemoteService(String domain, Remote remote) {
        onDebug(log -> log.debug("[{}] before register remote {} with service {}", name, remote.name, domain));
        final int index = findOrAddRemoteDomain(domain);
        ConcurrentSkipListSet<Remote> remotes = remoteServices.get(index);
        if (remotes == null) {
            remotes = new ConcurrentSkipListSet<>(Remote.weightComparator);
            remoteServices.put(index, remotes);
        }
        remotes.add(remote);
    }

    protected void removeRemoteService(String domain, Remote toRemove) {
        int index = remoteDomains.indexOf(domain);
        if (index == -1 && !route) return;
        else if (index == -1) index = remoteDomains.indexOf(domain + ROUTE_MARK);
        if (index == -1) return;
        remoteServices.get(index).remove(toRemove);
    }

    protected int prepareRemoteDomain(String domain) {
        if (remoteDomains.contains(domain)) {
            return -1;
        }
        remoteDomains.add(domain);
        return remoteDomains.size() - 1;
    }

    protected int findOrAddRemoteDomain(String domain) {
        final int index = remoteDomains.indexOf(domain);
        if (index != -1) return index;
        remoteDomains.add(domain);
        return remoteDomains.size() - 1;
    }

    //endregion

    @Getter final AtomicBoolean debug = new AtomicBoolean(false);
    @Getter final AtomicBoolean trace = new AtomicBoolean(false);
    @Getter final AtomicReference<Duration> timeout = new AtomicReference<>(Duration.ofSeconds(2));

    protected void purify() {
        services.clear();
        handlers.clear();
        handlerSignatures.clear();

        servers.forEach((k, v) -> {
            if (!v.isDisposed()) v.dispose();
        });
        servers.clear();
        remotes.forEach((k, v) -> {
            if (!v.socket.isDisposed()) v.socket.dispose();
        });
        remotes.clear();
        remoteNames.clear();
        remoteDomains.clear();
        remoteServices.clear();
        routes.get().clear();
    }

    protected String dump() {
        return String.valueOf(remoteDomains);
    }
}
