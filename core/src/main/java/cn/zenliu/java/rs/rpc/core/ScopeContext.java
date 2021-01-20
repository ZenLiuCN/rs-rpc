package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import lombok.Getter;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import reactor.core.Disposable;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Hold all Context data in a Scope
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-20
 */
abstract class ScopeContext {
    static char ROUTE_MARK = '?';
    /**
     * Scope Name
     */
    @Getter final String name;
    /**
     * Scope route ability
     */
    @Getter final boolean route;
    /**
     * remotes domain registry
     */
    @Getter final Map<Integer, ConcurrentSkipListSet<Remote>> remoteServices = new ConcurrentSkipListMap<>();


    //region Remotes info section
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
    //region routes Section: all service domain supported include in remotes if with route supported.
    @Getter final AtomicReference<Set<String>> routes = new AtomicReference<>();
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
    //endregion
    @Getter final AtomicBoolean debug = new AtomicBoolean(false);
    @Getter final AtomicReference<Duration> timeout = new AtomicReference<>(Duration.ofSeconds(2));

    protected ScopeContext(String name, boolean route) {
        this.name = name;
        this.route = route;
    }

    protected int prepareRemoteDomain(String domain) {
        if (remoteDomains.contains(domain)) {
            return -1;
        }
        remoteDomains.add(domain);
        return remoteDomains.size() - 1;
    }

    protected int findRemoteDomain(String domain) {
        return remoteDomains.indexOf(domain);
    }

    protected int findOrAddRemoteDomain(String domain) {
        final int index = remoteDomains.indexOf(domain);
        if (index > 0) return index;
        remoteDomains.add(domain);
        return remoteDomains.size() - 1;
    }

    //endregion

    protected void addOrUpdateRemoteService(String domain, Remote remote) {
        final int index = findOrAddRemoteDomain(domain);
        ConcurrentSkipListSet<Remote> remotes = remoteServices.get(index);
        if (remotes == null) {
            remotes = new ConcurrentSkipListSet<>(Remote.weightComparator);
            remoteServices.put(index, remotes);
        }
        remotes.add(remote);
    }

    protected void remoteRemoteService(String domain, Remote toRemove) {
        int index = findRemoteDomain(domain);
        if (index == -1 && !route) return;
        else if (index == -1) index = findRemoteDomain(domain + ROUTE_MARK);
        if (index == -1) return;
        remoteServices.get(index).remove(toRemove);
    }
    //endregion

    //region Local info section

    protected @Nullable Remote findRemoteService(String domain) {
        int index = findRemoteDomain(domain);
        if (index == -1 && !route) return null;
        else if (index == -1) index = findRemoteDomain(domain + ROUTE_MARK);
        if (index == -1) return null;
        final ConcurrentSkipListSet<Remote> remotes = remoteServices.get(index);
        if (remotes.isEmpty()) {
/*            if(findRemoteDomain(domain)!=-1) remoteDomains.remove(domain);
            else remoteDomains.remove(domain+ROUTE_MARK);
            remoteServices.remove(index);*/
            return null;
        }
        return remotes.first();
    }

    protected Optional<Remote> findRemoteServiceOptional(String domain) {
        return Optional.ofNullable(findRemoteService(domain));
    }

    protected int prepareRemoteName(String name) {
        if (remoteNames.contains(name)) {
            return -1;
        }
        remoteNames.add(name);
        return remoteNames.size() - 1;
    }

    protected int findRemoteName(String name) {
        return remoteNames.indexOf(name);
    }

    protected void updateRemoteService(Remote remote, Remote old) {
        for (String domain : remote.service) {
            addOrUpdateRemoteService(domain, remote);
        }
        if (old != null) {
            old.service.forEach(v -> remoteRemoteService(v, old));
        }
        calcRoutes();
    }

    protected void calcRoutes() {
        Set<String> routes = new HashSet<>(services);
        if (route)
            routes.addAll(Seq.seq(remoteDomains).map(x -> x.endsWith(ROUTE_MARK + "") ? x : (x + ROUTE_MARK)).toSet());
        this.routes.set(routes);
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
        if (services.contains(service)) {
            return false;
        }
        return services.add(service);
    }

    protected void addServer(Disposable server, String name) {
        servers.put(name, server);
    }

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
        final int index = prepareHandlerSignature(handlerSignature);
        if (index == -1) throw new IllegalStateException("a handler '" + handlerSignature + "' already exists! ");
        handlers.put(index, handler);
    }

    protected @Nullable Function<Object[], Result<Object>> findHandler(String handlerSignature) {
        final int index = findHandlerSignature(handlerSignature);
        if (index == -1) return null;
        return handlers.get(index);
    }

    protected Optional<Function<Object[], Result<Object>>> findHandlerOptional(String handlerSignature) {
        return Optional.ofNullable(findHandler(handlerSignature));

    }

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
