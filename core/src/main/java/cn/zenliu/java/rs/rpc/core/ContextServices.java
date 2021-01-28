package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface ContextServices extends Context {
    Map<Class<?>, WeakReference<Object>> getServices();

    Map<Class<?>, WeakReference<Object>> getProxies();

    Map<Integer, Function<Object[], Result<Object>>> getHandlers();

    UniqueList getSigns();

    default int prepareHandlerSign(String sign) {
        return getSigns().prepare(sign);
    }

    default void addHandler(String sign, Function<Object[], Result<Object>> handler) {
        onDebug("before register handler: \nsign: {},\nregistry: {}{} => {}", sign, getSigns(), getHandlers(), getServices());
        final int index = prepareHandlerSign(sign);
        if (index == -1) throw new IllegalStateException("a handler '" + sign + "' already exists! ");
        getHandlers().put(index, handler);
        onDebug("after register handler: \nsign: {}\nregistry: {}{} => {}", sign, getSigns(), getHandlers(), getServices());
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
        for (Map.Entry<Class<?>, WeakReference<Object>> entry : getServices().entrySet()) {
            if (entry.getValue().get() != null) {
                service.add(entry.getKey().getCanonicalName());
            } else {
                getServices().remove(entry.getKey());
            }
        }
        if (other != null) other.withRouteMark(service::add, service);
        return service;
    }

    default List<String> getProxiesList() {
        List<String> list = new ArrayList<>();
        for (WeakReference<Object> x : getProxies().values()) {
            Object o = x.get();
            if (o != null) {
                list.add(o.toString());
            }
        }
        return list;
    }

    default Object addProxy(Class<?> type, Object instance) {
        return getProxies().put(type, new WeakReference<>(instance));
    }

    default Object validateProxy(Class<?> type) {
        final WeakReference<Object> ref = getProxies().get(type);
        return ref == null ? null : ref.get();
    }

    default @Nullable Function<Object[], Result<Object>> findHandler(String sign) {
        final int index = getSigns().indexOf(sign);
        if (index == -1) return null;
        return getHandlers().get(index);
    }
}
