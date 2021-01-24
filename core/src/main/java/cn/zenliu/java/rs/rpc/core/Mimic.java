package cn.zenliu.java.rs.rpc.core;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Mimic is a Nest Deep Delegator<br>
 * 1. support interface in getters <br>
 * 2. support interface in Map Value <br>
 * 3. support interface in List Value <br>
 * For Simple Value (means without Interface inside), use {@link Delegator}.
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
@Slf4j
public class Mimic<T> implements InvocationHandler, cn.zenliu.java.rs.rpc.api.Delegator<T> {
    private final Class<T> type;
    private final Map<String, Object> values;
    private final Map<String, Integer> deep;
    static final int DEEP_LIST = 1;
    static final int DEEP_MAP_VALUE = 2;
    static final int DEEP_VALUE = 3;
    private Constructor<MethodHandles.Lookup> constructor;
    private transient Object[] result;

    private Object[] getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result;
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
        if (!accessible.isAccessible())
            accessible.setAccessible(true);
        return accessible;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        int length = (args == null ? 0 : args.length);
        if (length == 0 && name.startsWith("is")) {
            return values.get(name.substring(2));
        } else if (length == 0 && name.startsWith("get")) {
            final String field = name.substring(3);
            final Object v = values.get(field);
            if (deep.containsKey(field)) {
                switch (deep.get(field)) {
                    case DEEP_LIST:
                        return Seq.seq((List<Mimic>) v).map(Mimic::delegate).toList();
                    case DEEP_VALUE:
                        return ((Mimic) v).delegate();
                    case DEEP_MAP_VALUE:
                        return Seq.seq((Map<Object, Mimic>) v).map(x -> x.map2(Mimic::delegate)).toMap(Tuple2::v1, Tuple2::v2);
                    default:
                        throw new IllegalStateException("not accepted deep type:" + name + " as type " + deep.get(field));
                }
            } else {
                return v;
            }
        } else if (length == 1 && name.startsWith("set")) {
            return values.put(name.substring(3), args);
        } else if (length == 0 && name.equals("toString")) {
            return type + values.toString();
        } else if (method.isDefault()) {
            if (result == null) {
                getResult();
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

    Mimic(Class<T> type, Map<String, Object> values, Map<String, Integer> deep) {
        this.type = type;
        this.values = values;
        this.deep = deep;
    }

    @Override
    public String toString() {
        return type + "$Mimic" + values;
    }

    /**
     * create proxy instance from mimic
     */
    @Override
    public T delegate() {
        return type.cast(getResult()[0]);
    }

    static final Map<Class<?>, Function<Object, Mimic<?>>> delegator = new ConcurrentHashMap<>();

    /**
     * build a mimic
     *
     * @param instance source data
     * @param type     target type
     * @param <T>      type
     * @return a mimic instance
     */
    public static <T> Mimic<T> build(Object instance, Class<T> type) {
        if (delegator.containsKey(type)) {
            return (Mimic<T>) delegator.get(type).apply(instance);
        }
        final List<Method> methods = Seq.of(type.getMethods()).filter(x -> x.getName().startsWith("is") || x.getName().startsWith("get")).toList();
        final Function<Object, Mimic<?>> gen = i -> {
            final Map<String, Object> values = new ConcurrentHashMap<>();
            final Map<String, Integer> deep = new HashMap<>();
            for (Method method : methods) {
                final String field = method.getName().startsWith("is") ? method.getName().substring(2) : method.getName().substring(3);
                final Class<?> re = method.getReturnType();
                if (!re.isInterface()) {
                    values.put(field, sneakyInvoker(i, method));
                } else if (List.class.isAssignableFrom(re)) {
                    final List<Object> lists = (List<Object>) sneakyInvoker(i, method);
                    if (lists == null || lists.isEmpty()) {
                        log.debug("can't reference a container with no instance value: {} {}", field, re);
                    } else {
                        final Object o = lists.get(0);
                        final Class<?> v = o.getClass();
                        if (v.isEnum() || v.isArray() || v.isPrimitive()) {
                            values.put(field, lists);
                        } else {
                            final Class<?>[] interfaces = v.getInterfaces();
                            if (interfaces == null || interfaces.length == 0) {
                                log.debug("can't reference a container with no interface : {} {}", field, v);
                                values.put(field, lists);
                            } else {
                                log.debug("try fix interfaces {} {}", field, interfaces);
                                final Class<?> anInterface = interfaces[0];
                                values.put(field, Seq.seq(lists).map(x -> build(x, anInterface)).toList());
                                deep.put(field, DEEP_LIST);
                            }
                        }
                    }
                } else if (Map.class.isAssignableFrom(re)) {
                    final Map<Object, Object> map = (Map<Object, Object>) sneakyInvoker(i, method);
                    if (map == null || map.isEmpty()) {
                        log.debug("can't reference a container with no instance value: {} {}", field, re);
                    } else {
                        final Object o = map.get(0);
                        final Class<?> v = o.getClass();
                        if (v.isEnum() || v.isArray() || v.isPrimitive()) {
                            values.put(field, map);
                        } else {
                            final Class<?>[] interfaces = v.getInterfaces();
                            if (interfaces == null || interfaces.length == 0) {
                                log.debug("can't reference a container with no interface : {} {}", field, v);
                                values.put(field, map);
                            } else {
                                log.debug("try fix interfaces {} {}", field, interfaces);
                                final Class<?> anInterface = interfaces[0];
                                values.put(field, Seq.seq(map).map(x -> x.map2(y -> build(y, anInterface))).toMap(Tuple2::v1, Tuple2::v2));
                                deep.put(field, DEEP_MAP_VALUE);
                            }
                        }
                    }
                } else {
                    values.put(field, build(sneakyInvoker(i, method), re));
                    deep.put(field, DEEP_VALUE);
                }
            }
            return new Mimic<T>(type, values, deep);
        };
        delegator.put(type, gen);
        return (Mimic<T>) delegator.get(type).apply(instance);
    }

    @SneakyThrows
    static Object sneakyInvoker(Object instance, Method method, Object... args) {
        return method.invoke(instance, args);
    }

    public static AtomicReference<Predicate<String>> delegateInterfaceName = new AtomicReference<>(x -> true);

    @SuppressWarnings("unchecked")
    public static Object autoBuild(Object instance) {
        if (instance == null) return instance;
        final Class<?> aClass = instance.getClass();
        if (aClass.isPrimitive() || aClass.isEnum() || aClass.isArray()) return instance;
        if (List.class.isAssignableFrom(aClass)) {
            List<Object> a = (List<Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(Mimic::autoBuild).toList();
        } else if (Map.class.isAssignableFrom(aClass)) {
            Map<Object, Object> a = (Map<Object, Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(x -> x.map2(Mimic::autoBuild)).toMap(Tuple2::v1, Tuple2::v2);
        }
        final Class<?>[] interfaces = aClass.getInterfaces();
        if (interfaces == null || interfaces.length == 0 || !delegateInterfaceName.get().test(interfaces[0].getName()))
            return instance;
        return build(instance, interfaces[0]);
    }

    @SuppressWarnings({"DuplicatedCode", "unchecked"})
    public static Object autoDelegate(Object instance) {
        if (instance instanceof Mimic) {
            return ((Mimic<?>) instance).delegate();
        } else if (instance instanceof Map) {
            Map<Object, Object> a = (Map<Object, Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(x -> x.map2(Mimic::autoDelegate)).toMap(Tuple2::v1, Tuple2::v2);
        } else if (instance instanceof List) {
            List<Object> a = (List<Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(Mimic::autoDelegate).toList();
        }
        return instance;
    }
}
