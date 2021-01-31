package cn.zenliu.java.rs.rpc.core.context;


import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.RouteMeta;
import cn.zenliu.java.rs.rpc.core.proto.RouteMetaImpl;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.zenliu.java.rs.rpc.core.element.Remote.UNKNOWN_NAME;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextRoutes extends ContextRemoteServices, ContextServices {
    char ROUTE_MARK = '?';

    static String deRouteMark(String x) {
        final int idx = x.lastIndexOf(ROUTE_MARK);
        if (idx == -1) return x;
        return x.substring(0, idx);
    }

    AtomicReference<Set<String>> getRoutes();

    default void updateRoutes(Remote... exclude) {
        final Set<String> routes = getServiceName(isRoute() ? getDomains() : null);
        onDebug("update routes {} to {} ", () -> new Object[]{getRoutes().get(), routes});
        getRoutes().set(routes);
        syncServMeta(exclude);
    }

    default RouteMeta buildServMeta(@Nullable Remote target) {
        final Set<String> routes = getRoutes().get();
        onDebug("routes to building ServMeta is {}", routes);
        final List<String> service = new ArrayList<>();
        if (target != null && !target.getRoutes().isEmpty()) {
            for (String route : routes) {
                if (!target.getRoutes().contains(deRouteMark(route)) && !target.getRoutes().contains(route)) {
                    service.add(route);
                }
            }
        } else {
            service.addAll(routes);
        }
        final RouteMetaImpl.RouteMetaImplBuilder builder = RouteMetaImpl.builder()
            .name(getName())
            .routes(service);
        if (target != null) builder.known(target.getRoutes());
        return builder.build();
    }

    default void processRemoteUpdate(Remote in, Remote old, boolean known) {
        if (old == null && in.getName().equals(UNKNOWN_NAME)) {
            onDebug("a new connection found {} \n sync serv meta :{}", in, getRoutes().get());
            getRemotes().put(in.getIndex(), in);
            if (!known) pushServMeta(null, in);//todo
            return;
        }
        final int remoteIdx;
        if (old != null && old.getName().equals(UNKNOWN_NAME)) {
            remoteIdx = prepareRemoteName(in.getName());
            getRemotes().remove(old.getIndex());
            //  if (!known) pushServMeta(null, in);//push current meta to this remote for first update real Meta
        } else {
            remoteIdx = old != null && old.getIndex() >= 0 ? old.getIndex() : prepareRemoteName(in.getName());
        }
        if (getRemotes().containsKey(remoteIdx)) {
            in.setIndex(remoteIdx);
            final Remote oldRemote = getRemotes().get(remoteIdx);
            in.setWeight(Math.max(oldRemote.getWeight(), 1));
            onDebug("update meta for remote {}[{}] ", in, remoteIdx);
            getRemotes().put(remoteIdx, in);
            onDebug("remove and update meta \n FROM {} \nTO {}", old, in);
        } else {
            onDebug("new meta from remote {}[{}] ", in, remoteIdx);
            if (in.getIndex() == -1) in.setIndex(remoteIdx);
            if (in.getWeight() == 0) in.setWeight(1);
            getRemotes().put(remoteIdx, in);
        }
        if (updateRemoteService(in, old)) {
            updateRoutes(in);
        }
        if (!known) {
            onDebug("remove and update meta \n FROM {} \nTO {} with sync ", old, in);
            pushServMeta(null, in);
        }
    }

    /**
     * Sync Meta data: push meta to all remotes
     */
    default void syncServMeta(Remote... exclude) {
        if (getRemotes().isEmpty()) return;
        final List<Remote> excludes = Arrays.asList(exclude);
        onDebug("will sync serv meta {} to remote {}  ", getRoutes().get(), getRemotes());
        getRemotes().forEach((i, v) -> {
            if (excludes.contains(v)) {
                onDebug("skip remote {} for is marked as Skip", getRemoteNames().get(i));
                return;
            }
            onDebug("sync serv meta to remote [{}]: {} ", v, getRoutes().get());
            pushServMeta(null, v);
        });
    }

    default void pushServMeta(@Nullable RouteMeta servMeta, Remote target) {
        target.pushRouteMeta(servMeta == null ? buildServMeta(target) : servMeta);
    }

    @Override
    default boolean addService(Class<?> clazz, Object service) {
        final boolean r = ContextServices.super.addService(clazz, service);
        if (r) updateRoutes();
        return r;
    }
}

