package cn.zenliu.java.rs.rpc.core.element;

import lombok.AllArgsConstructor;
import mimic.Cache;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

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

    @AllArgsConstructor(staticName = "of")
    final class RouteTableImpl implements RouteTable {
        final Map<String, ConcurrentSkipListSet<Remote>> registry = new ConcurrentHashMap<>();
        final Map<String, ServiceMethod> methods = new ConcurrentHashMap<>();
        final Map<String, ServiceStreamMethod> streams = new ConcurrentHashMap<>();
        final Cache<Class<?>, Object> services = Cache.build(null, false);
        final Logger log;

        static <T> T safeFirst(ConcurrentSkipListSet<T> list) {
            return list == null || list.isEmpty() ? null : list.first();
        }

        @Override
        public @Nullable Remote findRoute(String address) {
            return safeFirst(registry.get(address));
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
            return null;
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
