package cn.zenliu.java.rs.rpc.api;

import lombok.SneakyThrows;
import lombok.ToString;
import org.jooq.lambda.Seq;
import org.jooq.lambda.Sneaky;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Delegator use to act as Pojo for a Interface.
 *
 * @author Zen.Liu
 * @apiNote Delegator
 * @since 2021-01-12
 */
@ToString
public final class Delegator {
    /**
     * Copier Cache
     */
    public static final Map<Class<?>, Function<Object, Map<String, Object>>> copier = new ConcurrentHashMap<>();
    /**
     * Delegate target type
     */
    public final Class<?> type;
    /**
     * 值
     */
    public final Map<String, Object> values;
    private Constructor<MethodHandles.Lookup> constructor;

    Delegator(Class<?> type) {
        this.type = type;
        this.values = new HashMap<>();
    }

    Delegator(Class<?> type, Map<String, Object> values) {
        this.type = type;
        this.values = values == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(values);
    }

    /**
     * Build a Delegate Proxy via Initial values
     *
     * @param clz  target class must a interface
     * @param init Initial values, key must a javabean getter field with first letter Capital
     * @param <T>  Delegate type
     * @return Proxy Instance
     */
    public static <T> T proxy(Class<T> clz, Map<String, Object> init) {
        return new Delegator(clz, init).proxy(clz);
    }

    /**
     * Build a Delegate Proxy via Instance values
     *
     * @param clz      target class must a interface
     * @param instance instance to copy
     * @param <T>      Delegate type
     * @return Proxy Instance
     */
    public static <T> T proxy(Class<T> clz, T instance) {
        return new Delegator(clz, copy(instance, clz)).proxy(clz);
    }

    /**
     * Copy Instance values to Map
     *
     * @param instance target Instance
     * @param face     interface of Instance
     * @return data, which use for {@link Delegator#proxy}
     */
    public static Map<String, Object> copy(Object instance, Class<?> face) {
        if (copier.containsKey(face)) {
            return copier.get(face).apply(instance);
        }
        final List<Tuple2<String, Function<Object, Object>>> m = Seq.of(face.getMethods()).map(x ->
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
    public static <T> T proxy(T noneNestedInterfaceObject) {
        final Class<?> iface = noneNestedInterfaceObject.getClass().getInterfaces()[0];
        return proxy((Class<T>) iface, noneNestedInterfaceObject);
    }

    static <T extends AccessibleObject> T accessible(T accessible) {
        if (accessible == null) {
            return null;
        }

        if (accessible instanceof Member) {
            Member member = (Member) accessible;

            if (Modifier.isPublic(member.getModifiers()) &&
                Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                return accessible;
            }
        }

        // [jOOQ #3392] The accessible flag is set to false by default, also for public members.
        if (!accessible.isAccessible())
            accessible.setAccessible(true);
        return accessible;
    }

    /**
     * Create a no value Proxy
     *
     * @param clz Interface Class
     * @param <T> Interface Type
     * @return a Proxy instance
     */
    @SuppressWarnings("unchecked")
    public <T> T proxy(Class<T> clz) {
        final Object[] result = new Object[1];
        final InvocationHandler handler = new InvocationHandler() {
            @SuppressWarnings("null")
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) {
                String name = method.getName();
                int length = (args == null ? 0 : args.length);
                if (length == 0 && name.startsWith("is")) {
                    return values.get(name.substring(2));
                } else if (length == 0 && name.startsWith("get")) {
                    return values.get(name.substring(3));
                } else if (length == 1 && name.startsWith("set")) {
                    return values.put(name.substring(3), args);
                } else if (length == 0 && name.equals("toString")) {
                    return type + values.toString();
                } else if (method.isDefault()) {
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
        };
        result[0] = Proxy.newProxyInstance(clz.getClassLoader(), new Class[]{clz}, handler);
        return (T) result[0];
    }

    /**
     * Create a Proxy from current Instance
     */
    @SneakyThrows
    public Object delegate() {
        return proxy(type);
    }
}

