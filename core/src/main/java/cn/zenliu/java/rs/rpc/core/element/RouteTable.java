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
import java.util.function.Function;

/**
 * manage all services (local and remote)
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface RouteTable {
    @Nullable Remote findRoute(String address);

    RouteTable addRoute(RouteMeta meta, Remote remote);


    boolean addService(Class<?> clazz, Object service);

    @Nullable ServiceMethod findMethod(String address);

    @Nullable ServiceStreamMethod findStreamMethod(String address);

    void addStreamMethod(String address, ServiceStreamMethod method);

    void addMethod(String address, ServiceMethod method);

    final class WeightedRemote {
        final Remote remote;
        final Integer jump;

        WeightedRemote(Remote remote, Integer jump) {
            this.remote = remote;
            this.jump = jump;
        }

        WeightedRemote updateWeight(Integer jump) {
            return new WeightedRemote(remote, jump);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof WeightedRemote)) return false;
            WeightedRemote that = (WeightedRemote) o;
            return remote.equals(that.remote);
        }

        @Override
        public int hashCode() {
            return Objects.hash(remote);
        }
    }

    final class RouteTableImpl implements RouteTable {
        final Map<String, ConcurrentSkipListSet<WeightedRemote>> registry = new ConcurrentHashMap<>();
        final Map<String, ServiceMethod> methods = new ConcurrentHashMap<>();
        final Map<String, ServiceStreamMethod> streams = new ConcurrentHashMap<>();
        final Cache<Class<?>, Object> services = Cache.build(null, false);
        final Logger log;

        private RouteTableImpl(Logger log) {
            this.log = log;
            RemoteObserverUtil.observer.asFlux().subscribe(e -> {
                final List<Tuple2<WeightedRemote, ConcurrentSkipListSet<WeightedRemote>>> remotes = findRemote(e.v2);
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

        private List<Tuple2<WeightedRemote, ConcurrentSkipListSet<WeightedRemote>>> findRemote(Remote remote) {
            List<Tuple2<WeightedRemote, ConcurrentSkipListSet<WeightedRemote>>> result = new ArrayList<>();
            for (ConcurrentSkipListSet<WeightedRemote> value : registry.values()) {
                for (WeightedRemote weightedRemote : value) {
                    if (weightedRemote.remote.equals(remote)) {
                        result.add(Tuple.tuple(weightedRemote, value));
                    }
                }
            }
            return result;
        }

        static <T, R> R safeFirst(ConcurrentSkipListSet<T> list, Function<T, R> notNull) {
            return list == null || list.isEmpty() ? null : notNull.apply(list.first());
        }

        public static RouteTableImpl of(Logger log) {
            return new RouteTableImpl(log);
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
                ConcurrentSkipListSet<WeightedRemote> lists = registry.get(address);
                final WeightedRemote weightedRemote = new WeightedRemote(remote, r.jumps());
                if (lists == null)
                    lists = new ConcurrentSkipListSet<>(Comparator.comparingInt(x -> x.remote.getIndex() + x.jump));
                else
                    lists.remove(weightedRemote);
                lists.add(weightedRemote);
                registry.put(address, lists);
            }
            return this;
        }

        @Override
        public boolean addService(Class<?> clazz, Object service) {
            log.onDebug("before to register service {} into {}", service, services);
            if (services.containsKey(clazz)) {
                log.error("fail to register a exists service {} into {}", service, services);
                return false;
            }
            try {
                services.put(clazz, new WeakReference<>(service));
                return true;
            } finally {
                log.onDebug("after to register a  service {} into {}", service, services);
            }
        }

        @Override
        public void addMethod(String address, ServiceMethod method) {
            log.onDebug("before register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
            if (methods.containsKey(address))
                throw new IllegalStateException("a ServiceMethod '" + address + "' already exists! ");
            methods.put(address, method);
            log.onDebug("after register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
        }

        @Override
        public void addStreamMethod(String address, ServiceStreamMethod method) {
            log.onDebug("before register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, streams, services});
            if (streams.containsKey(address))
                throw new IllegalStateException("a StreamMethod '" + address + "' already exists! ");
            streams.put(address, method);
            log.onDebug("after register method: \naddress: {},\nregistry: {}=> {}", () -> new Object[]{address, methods, services});
        }
    }
}
