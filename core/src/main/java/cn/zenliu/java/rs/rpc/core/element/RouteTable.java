package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.core.util.RemoteObserverUtil;
import mimic.Cache;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * manage all services (local and remote)
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface RouteTable {
    /**
     * find a  Remote match the address (minimal delay and jump)
     *
     * @param address address
     * @return null if not found
     */
    @Nullable Remote findRoute(String address);

    /**
     * process route meta from remote
     *
     * @param meta   the meta
     * @param remote the remote
     * @return self
     */
    RouteTable addRoute(RouteMeta meta, Remote remote);

    /**
     * register local service
     *
     * @param clazz   service interface type
     * @param service service instance
     * @return dose exists same service
     */

    boolean addService(Class<?> clazz, Object service);

    /**
     * find a  Method match the address
     *
     * @param address address
     * @return null if not found
     */
    @Nullable ServiceMethod findMethod(String address);

    /**
     * find a Stream Method match the address
     *
     * @param address address
     * @return null if not found
     */
    @Nullable ServiceStreamMethod findStreamMethod(String address);

    /**
     * add local stream service method
     *
     * @param address the address
     * @param method  the method
     */
    void addStreamMethod(String address, ServiceStreamMethod method);

    /**
     * add a local Service Method
     *
     * @param address the method address
     * @param method  the method
     */
    void addMethod(String address, ServiceMethod method);

    /**
     * build a route meta to send to remote
     *
     * @param remote optional old remote
     * @return RouteMeta
     */
    RouteMeta buildMeta(@Nullable Remote remote);

    final class RouteRemote {
        final Remote remote;
        final Route route;

        RouteRemote(Remote remote, Route route) {
            this.remote = remote;
            this.route = route;
        }


        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof RouteRemote)) return false;
            RouteRemote that = (RouteRemote) o;
            return remote.equals(that.remote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(remote);
        }
    }

    final class RouteTableImpl implements RouteTable {
        final Map<String, ConcurrentSkipListSet<RouteRemote>> registry = new ConcurrentHashMap<>();
        final Map<String, ServiceMethod> methods = new ConcurrentHashMap<>();
        final Map<String, ServiceStreamMethod> streams = new ConcurrentHashMap<>();
        final Cache<Class<?>, Object> services = Cache.build(null, false);
        final AtomicReference<Set<String>> routes = new AtomicReference<>();
        final Context ctx;

        private RouteTableImpl(Context ctx) {
            this.ctx = ctx;
            RemoteObserverUtil.observer.asFlux().subscribe(e -> {
                final List<Tuple2<RouteRemote, ConcurrentSkipListSet<RouteRemote>>> remotes = findRemote(e.v2);
                if (remotes.isEmpty()) return;
                switch (e.v1) {
                    case WEIGHT_CHANGE:
                        remotes.forEach(x -> {
                            x.v2.remove(x.v1);
                            x.v2.add(x.v1);
                        });
                        break;
                    case REMOVED:
                        remotes.forEach(x -> x.v2.remove(x.v1));
                        break;
                }
            });
        }

        private List<Tuple2<RouteRemote, ConcurrentSkipListSet<RouteRemote>>> findRemote(Remote remote) {
            List<Tuple2<RouteRemote, ConcurrentSkipListSet<RouteRemote>>> result = new ArrayList<>();
            for (ConcurrentSkipListSet<RouteRemote> value : registry.values()) {
                for (RouteRemote routeRemote : value) {
                    if (routeRemote.remote.equals(remote)) {
                        result.add(Tuple.tuple(routeRemote, value));
                    }
                }
            }
            return result;
        }

        static <T, R> R safeFirst(ConcurrentSkipListSet<T> list, Function<T, R> notNull) {
            return list == null || list.isEmpty() ? null : notNull.apply(list.first());
        }

        static RouteTableImpl of(Context ctx) {
            return new RouteTableImpl(ctx);
        }

        @Override
        public @Nullable Remote findRoute(String address) {
            return safeFirst(registry.get(address), x -> x.remote);
        }

        @Override
        public @Nullable ServiceMethod findMethod(String address) {
            return methods.get(address);
        }

        @Override
        public @Nullable ServiceStreamMethod findStreamMethod(String address) {
            return streams.get(address);
        }

        @Override
        public RouteTable addRoute(RouteMeta meta, Remote remote) {
            for (String s : meta.getRoutes()) {
                Route r = Route.fromRoute(s);
                final String address = r.getAddress();
                ConcurrentSkipListSet<RouteRemote> lists = registry.get(address);
                final RouteRemote routeRemote = new RouteRemote(remote, r);
                if (lists == null)
                    lists = new ConcurrentSkipListSet<>(Comparator.comparingInt(x -> x.remote.getIndex() + x.route.jumps()));
                else
                    lists.remove(routeRemote);
                lists.add(routeRemote);
                registry.put(address, lists);
            }
            return this;
        }

        @Override
        public boolean addService(Class<?> clazz, Object service) {
            ctx.onDebug("before to register service {} into {}", service, services);
            if (services.containsKey(clazz)) {
                ctx.error("fail to register a exists service {} into {}", service, services);
                return false;
            }
            try {
                services.put(clazz, new WeakReference<>(service));
                return true;
            } finally {
                ctx.onDebug("after to register a  service {} into {}", service, services);
            }
        }

        @Override
        public void addMethod(String address, ServiceMethod method) {
            ctx.onDebug("before register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
            if (methods.containsKey(address))
                throw new IllegalStateException("a ServiceMethod '" + address + "' already exists! ");
            methods.put(address, method);
            ctx.onDebug("after register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
        }

        private void buildRoute() {
            final Set<String> routes = new HashSet<>();
            routes.addAll(methods.keySet());
            routes.addAll(streams.keySet());
            for (ConcurrentSkipListSet<RouteRemote> x : registry.values()) {
                Route append = x.first().route.append(ctx.getName());
                String toRoute = append.toRoute();
                routes.add(toRoute);
            }
            this.routes.set(routes);
        }

        @Override
        public RouteMeta buildMeta(@Nullable Remote remote) {
            buildRoute();
            final Set<String> localRoutes = routes.get();
            return RouteMeta.from(ctx.getName(), localRoutes, remote == null ? null : remote.getRoutes());

        }

        @Override
        public void addStreamMethod(String address, ServiceStreamMethod method) {
            ctx.onDebug("before register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, streams, services});
            if (streams.containsKey(address))
                throw new IllegalStateException("a StreamMethod '" + address + "' already exists! ");
            streams.put(address, method);
            ctx.onDebug("after register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
        }
    }
}
