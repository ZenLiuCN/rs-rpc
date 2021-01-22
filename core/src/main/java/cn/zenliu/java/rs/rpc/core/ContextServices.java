package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface ContextServices extends Context {
    UniqueList getServices();

    Map<Integer, Function<Object[], Result<Object>>> getHandlers();

    UniqueList getSigns();

    default int prepareHandlerSign(String sign) {
        return getSigns().prepare(sign);
    }

    default void addHandler(String sign, Function<Object[], Result<Object>> handler) {
        onDebug(log -> log.debug("[{}] before register handler: \nsign: {},\nregistry: {}{} => {}", getName(), sign, getSigns(), getHandlers(), getServices()));
        final int index = prepareHandlerSign(sign);
        if (index == -1) throw new IllegalStateException("a handler '" + sign + "' already exists! ");
        getHandlers().put(index, handler);
        onDebug(log -> log.debug("[{}] after register handler: \nsign: {}\nregistry: {}{} => {}", getName(), sign, getSigns(), getHandlers(), getServices()));
    }

    default boolean addService(String service) {
        onDebug(log -> log.debug("[{}] before to register service {} into {}", getName(), service, getServices()));
        if (getServices().contains(service)) {
            withLog(log -> log.error("[{}] error to register a exists service {} into {}", getName(), service, getServices()));
            return false;
        }
        try {
            return getServices().add(service);
        } finally {
            onDebug(log -> log.debug("[{}] after to register a  service {} into {}", getName(), service, getServices()));
        }
    }

    default @Nullable Function<Object[], Result<Object>> findHandler(String sign) {
        final int index = getSigns().indexOf(sign);
        if (index == -1) return null;
        return getHandlers().get(index);
    }
}
