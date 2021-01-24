package cn.zenliu.java.rs.rpc.core;

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
 * Delegator is Most simple one, There should no nested interface exists in fields,or exists inside container <br>
 * For a Complex Structure , use {@link Mimic#build(Object, Class)} for nested delegate.
 *
 * @author Zen.Liu
 * @apiNote Delegator
 * @since 2021-01-12
 */

@ToString
public final class Delegator<T> implements InvocationHandler, cn.zenliu.java.rs.rpc.api.Delegator<T> {
    /**
     * Copier Cache
     */
    public static final Map<Class<?>, Function<Object, Map<String, Object>>> copier = new ConcurrentHashMap<>();
    public final Class<T> type;
    /**
     * 值
     */
    public final Map<String, Object> values;
    private Constructor<MethodHandles.Lookup> constructor;
    transient Object[] result;

    Delegator(Class<T> type) {
        this.type = type;
        this.values = new HashMap<>();
    }

    Delegator(Class<T> type, Map<String, Object> values) {
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
        return new Delegator<>(clz, init).delegate();
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
        return new Delegator<>(clz, copy(instance, clz)).delegate();
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
        if (!Mimic.delegateInterfaceName.get().test(iface.getCanonicalName())) {
            return noneNestedInterfaceObject;
        }
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


    Object getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result[0];
    }

    /**
     * Create a Proxy from current Instance
     */
    @SuppressWarnings("unchecked")
    @Override
    public T delegate() {
        return (T) getResult();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
            if (result == null) {
                result = new Object[1];
                result[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
            }
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
}

