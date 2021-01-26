package mimic;

import lombok.SneakyThrows;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Sneaky;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static mimic.ReflectUtil.accessible;
import static mimic.ReflectUtil.getterMethods;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Delegator use to act as Pojo for a Interface.
 *
 * @author Zen.Liu
 * @apiNote Delegator
 * @since 2021-01-12
 */
@ToString
public final class Proxy<T> implements InvocationHandler, Delegator<T> {
    /**
     * Copier Cache
     */
    public static final Map<Class<?>, Function<Object, Map<String, Object>>> copier = new ConcurrentHashMap<>();
    /**
     * Delegate target type
     */
    public final Class<T> type;
    public final ConcurrentHashMap<String, Object> values;
    private Constructor<MethodHandles.Lookup> constructor;
    private transient Object[] result;

    Proxy(Class<T> type) {
        this.type = type;
        this.values = new ConcurrentHashMap<>();
    }

    Proxy(Class<T> type, Map<String, Object> values) {
        this.type = type;
        this.values = values == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(values);
    }

    @SuppressWarnings("unchecked")
    private T getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return (T) result[0];
    }

    /**
     * Build a Delegate Proxy via Initial values
     *
     * @param clz  target class must a interface
     * @param init Initial values, key must a javabean getter field with first letter Capital
     * @param <T>  Delegate type
     * @return Proxy Instance
     */

    public static <T> T of(Class<T> clz, Map<String, Object> init) {
        return new Proxy<>(clz, init).disguise();
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
        return new Proxy<>(clz, copy(instance, clz)).getResult();
    }

    public static <T> Proxy<T> from(Class<T> clz, T instance) {
        return new Proxy<>(clz, copy(instance, clz));
    }

    public static <T> Proxy<T> from(Class<T> clz) {
        return new Proxy<>(clz, null);
    }

    /**
     * Copy Instance values to Map
     *
     * @param instance target Instance
     * @param face     interface of Instance
     * @return data, which use for {@link Proxy#of}
     */
    public static Map<String, Object> copy(Object instance, Class<?> face) {
        if (copier.containsKey(face)) {
            return copier.get(face).apply(instance);
        }
        final List<Tuple2<String, Function<Object, Object>>> m = getterMethods(face).map(x ->
            x.getName().startsWith("is") ? tuple(x.getName().substring(2), Sneaky.function(x::invoke)) :
                x.getName().startsWith("get") ? tuple(x.getName().substring(3), Sneaky.function(x::invoke)) : null
        ).filter(Objects::nonNull).collect(Collectors.toList());
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


    /**
     * Create a Proxy from current Instance
     */
    @SneakyThrows
    public T disguise() {
        return getResult();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        int length = (args == null ? 0 : args.length);
        if (length == 0 && name.startsWith("is")) {
            return NULL.restore(values.get(name.substring(2)));
        } else if (length == 0 && name.startsWith("get")) {
            return NULL.restore(values.get(name.substring(3)));
        } else if (length == 1 && name.startsWith("set")) {
            return values.put(name.substring(3), NULL.wrap(args[0]));
        } else if (length == 0 && name.equals("toString")) {
            return type + "$Proxy" + values.toString();
        } else if (length == 1 && name.equals("equals")) {
            return args[0].equals(values);
        } else if (length == 0 && name.equals("hashCode")) {
            return values.hashCode();
        } else if (method.isDefault()) {
            getResult();
            try {
                if (constructor == null)
                    constructor = accessible(MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class));
                Class<?> declaringClass = method.getDeclaringClass();
                return constructor
                    .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(result[0])
                    .invokeWithArguments(args);
            } catch (Throwable e) {
                throw new IllegalStateException("Cannot invoke default method", e);
            }
        } else {
            throw new IllegalStateException("not accepted call:" + name);
        }
    }

    @Override
    public Proxy<T> set(String field, Object value) {
        if (field == null || field.isEmpty()) throw new IllegalArgumentException("field should never be null or empty");
        if (!Character.isUpperCase(field.charAt(0)))
            throw new IllegalArgumentException("field should be Pascal Case (first char also upper cased)");
        values.put(field, NULL.wrap(value));
        return this;
    }

    @Override
    public @Nullable Object get(String field) {
        return NULL.restore(values.get(field));
    }

    @Override
    public String dump() {
        return type + "$Proxy" + values;
    }
}

