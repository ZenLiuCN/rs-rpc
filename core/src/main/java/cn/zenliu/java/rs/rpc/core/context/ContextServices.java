package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.core.element.UniqueList;
import mimic.Cache;
import org.jetbrains.annotations.Nullable;
import reactor.core.publisher.Flux;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextServices extends Context {
    Cache<Class<?>, Object> getServices();

    Cache<Class<?>, Object> getProxies();

    Map<Integer, Function<Object[], Result<Object>>> getHandlers();

    Map<Integer, Function<Object[], Flux<Object>>> getStreamHandlers();

    UniqueList getSigns();

    default int prepareHandlerSign(String sign) {
        return getSigns().prepare(sign);
    }

    default void addHandler(String sign, Function<Object[], Result<Object>> handler) {
        onDebug("before register handler: \nsign: {},\nregistry: {}{} => {}", () -> new Object[]{sign, getSigns(), getHandlers(), getServices()});
        final int index = prepareHandlerSign(sign);
        if (index == -1) throw new IllegalStateException("a handler '" + sign + "' already exists! ");
        getHandlers().put(index, handler);
        onDebug("after register handler: \nsign: {}\nregistry: {}{} => {}", () -> new Object[]{sign, getSigns(), getHandlers(), getServices()});
    }

    default void addStreamHandler(String sign, Function<Object[], Flux<Object>> handler) {
        onDebug("before register handler: \nsign: {},\nregistry: {}{} => {}", () -> new Object[]{sign, getSigns(), getStreamHandlers(), getServices()});
        final int index = prepareHandlerSign(sign);
        if (index == -1) throw new IllegalStateException("a handler '" + sign + "' already exists! ");
        getStreamHandlers().put(index, handler);
        onDebug("after register handler: \nsign: {}\nregistry: {}{} => {}", () -> new Object[]{sign, getSigns(), getStreamHandlers(), getServices()});
    }

    default boolean addService(Class<?> clazz, Object service) {
        onDebug("before to register service {} into {}", service, getServices());
        if (getServices().containsKey(clazz)) {
            error("fail to register a exists service {} into {}", service, getServices());
            return false;
        }
        try {
            getServices().put(clazz, new WeakReference<>(service));
            return true;
        } finally {
            onDebug("after to register a  service {} into {}", service, getServices());
        }
    }

    default Set<String> getServiceName(UniqueList other) {
        Set<String> service = new HashSet<>();
        for (Class<?> entry : getServices().getKeys()) {
            service.add(entry.getCanonicalName());
        }
        if (other != null) other.withRouteMark(service::add, service);
        return service;
    }

    default List<String> getProxiesList() {
        List<String> list = new ArrayList<>();
        for (Object o : getProxies().getValues()) {
            if (o != null) {
                list.add(o.toString());
            }
        }
        return list;
    }

    default Object addProxy(Class<?> type, Object instance) {
        return getProxies().put(type, instance);
    }

    default Object validateProxy(Class<?> type) {
        return getProxies().get(type);
    }

    default @Nullable Function<Object[], Result<Object>> findHandler(String sign) {
        final int index = getSigns().indexOf(sign);
        if (index == -1) return null;
        return getHandlers().get(index);
    }

    default @Nullable Function<Object[], Flux<Object>> findStreamHandler(String sign) {
        final int index = getSigns().indexOf(sign);
        if (index == -1) return null;
        return getStreamHandlers().get(index);
    }
}
