package cn.zenliu.java.rs.rpc.api;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface Delegator<T> {
    T delegate();

    static @Nullable Object tryRemoveProxy(Object maybeProxy) {
        try {
            Field h = maybeProxy.getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            return h.get(maybeProxy);
        } catch (Exception ex) {
            return null;
        }
    }

    static Optional<Object> tryRemoveProxyOptional(Object maybeProxy) {
        try {
            Field h = maybeProxy.getClass().getSuperclass().getDeclaredField("h");
            h.setAccessible(true);
            return Optional.of(h.get(maybeProxy));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }
}
