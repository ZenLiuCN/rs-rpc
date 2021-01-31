package mimic;

import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.Optional;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface Mimic<T> extends Serializable {
    @Nullable
    default MimicDeep<T> asDeep() {
        if (this instanceof MimicDeep) return (MimicDeep<T>) this;
        return null;
    }

    @Nullable
    default MimicLight<T> asLight() {
        if (this instanceof MimicLight) return (MimicLight<T>) this;
        return null;
    }

    @Nullable
    default MimicLambda<T> asLambda() {
        if (this instanceof MimicLambda) return (MimicLambda<T>) this;
        return null;
    }

    T disguise();

    Object get(String field);

    Mimic<T> set(String field, Object value);

    String dump();

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
