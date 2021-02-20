package mimic;

import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Sneaky;
import org.jooq.lambda.tuple.Tuple2;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import static mimic.ReflectUtil.getterMethodsMapping;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Delegator use to act as Pojo for a Interface.
 *
 * @author Zen.Liu
 * @apiNote Delegator
 * @since 2021-01-12
 */
public final class MimicLight<T> extends BaseMimic<T> {
    private static final long serialVersionUID = 5639462926823838734L;

    MimicLight(Class<T> type) {
        super(type, new ConcurrentHashMap<>());
    }

    MimicLight(Class<T> type, Map<String, Object> values) {
        super(type, values == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(values));
    }

    @Override
    public @Nullable Object get(String field) {
        return getter(field);
    }

    public MimicLight<T> set(String field, Object value) {
        validateField(field);
        setter(field, value);
        return this;
    }

    void setter(String field, Object value) {
        values.put(field, NULL.wrap(value));
    }

    Object getter(String field) {
        return NULL.restore(values.get(field));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MimicLight)) {
            final Object o1 = Mimic.tryRemoveProxy(o);
            if (!(o1 instanceof MimicLight)) return false;
            return equals(o1);
        }
        MimicLight<?> mimicLight = (MimicLight<?>) o;
        return type.equals(mimicLight.type) && values.equals(mimicLight.values);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, values);
    }

    @Override
    public String dump() {
        return type + "$Proxy" + values;
    }

    @Override
    public String toString() {
        return dump();
    }
    //region Static
    /**
     * Copier Cache
     */
    final static Cache<Class<?>, Function<Object, Map<String, Object>>> copier = Cache.build(null, true);

    /**
     * Build a Delegate Proxy via Initial values
     *
     * @param clz  target class must a interface
     * @param init Initial values, key must a javabean getter field with first letter Capital
     * @param <T>  Delegate type
     * @return Proxy Instance
     */

    public static <T> T of(Class<T> clz, Map<String, Object> init) {
        return new MimicLight<>(clz, init).disguise();
    }

    /**
     * Build a Delegate Proxy via Instance values
     *
     * @param clz      target class must a interface
     * @param instance instance to copy
     * @param <T>      Delegate type
     * @return Proxy Instance
     */
    public static <T> T of(Class<T> clz, T instance) {
        return new MimicLight<>(clz, copy(instance, clz)).disguise();
    }

    public static <T> MimicLight<T> from(Class<T> clz, T instance) {
        return new MimicLight<>(clz, copy(instance, clz));
    }

    public static <T> MimicLight<T> from(Class<T> clz) {
        return new MimicLight<>(clz, null);
    }

    /**
     * Copy Instance values to Map
     *
     * @param instance target Instance
     * @param face     interface of Instance
     * @return data, which use for {@link MimicLight#of}
     */
    public static Map<String, Object> copy(Object instance, Class<?> face) {
        if (copier.containsKey(face)) {
            return copier.get(face).apply(instance);
        }
        final List<Tuple2<String, Function<Object, Object>>> m = getterMethodsMapping(face, x ->
                x.getName().startsWith("is") ? tuple(x.getName().substring(2), Sneaky.function(x::invoke)) :
                    x.getName().startsWith("get") ? tuple(x.getName().substring(3), Sneaky.function(x::invoke)) : null,
            true
        );
        Function<Object, Map<String, Object>> extractor = o -> {
            final Map<String, Object> values = new HashMap<>();
            for (Tuple2<String, Function<Object, Object>> func : m) {
                values.put(func.v1, func.v2.apply(o));
            }
            return values;
        };
        synchronized (copier) {
            copier.put(face, extractor);
        }
        return extractor.apply(instance);
    }

    @SuppressWarnings("unchecked")
    public static <T> T of(T noneNestedInterfaceObject) {
        final Class<?> iface = noneNestedInterfaceObject.getClass().getInterfaces()[0];
        return of((Class<T>) iface, noneNestedInterfaceObject);
    }
    //endregion

}




