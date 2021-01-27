package mimic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static mimic.Lambda.findFirstReverse;
import static mimic.MimicType.mimicTypes;
import static mimic.ReflectUtil.*;
import static mimic.internal.*;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface MimicUtil {
    static void setInterfacePredicate(@Nullable Predicate<String> interfacePredicate) {
        interfaceNamePredicate.set(interfacePredicate == null ? x -> true : interfacePredicate);
    }

    /**
     * register a Static None Mimic Mapping Method for a Exactly Instance Class
     *
     * @param realType the instance class
     * @param mapping  function to convert instance to other instance (both never be a interface)
     * @return does replaced an old definition.
     */
    static boolean registerStaticMapping(Class<?> realType, Function<Object, Object> mapping) {
        return staticMapping.put(realType, mapping) != null;
    }

    static void clearStaticMapping() {
        staticMapping.clear();
    }

    /**
     * register a Static None Mimic Mapping Method for a  Instance Match the predicate
     *
     * @param predicate the method to predicate a Instance should mapping by hand
     * @param mapping   the mapping method.
     * @return does replaced an old definition.
     */
    static boolean registerPredicateMapping(Predicate<Object> predicate, Function<Object, Object> mapping) {
        return predicateMapping.put(predicate, mapping) != null;
    }

    static void clearPredicateMapping() {
        predicateMapping.clear();
    }

    /**
     * process common container
     *
     * @param instance  the instance
     * @param processor the processor
     * @return null or processor result
     */
    static Object containerProcess(Object instance, boolean realNull, Function<Object, Object> processor) {
        if (instance == null) return realNull ? null : NULL.Null;
        if (instance instanceof NULL) return realNull ? null : NULL.Null;

        for (Tuple2<Predicate<Object>, BiFunction<Function<Object, Object>, Object, Object>> p : containerProcessors) {
            if (p.v1.test(instance)) return p.v2.apply(processor, instance);
        }
        return instance;
    }

    /**
     * convert a Instance into a {@link Mimic} as Type
     *
     * @param instance the instance (not null)
     * @param type     the target type
     * @param <T>      type
     * @return a Mimic
     */
    @SuppressWarnings("unchecked")
    static <T> Mimic<T> mimic(@NotNull Object instance, @NotNull Class<T> type) {
        //use cache if possible
        if (delegator.containsKey(type)) {
            return (Mimic<T>) delegator.get(type).apply(instance);
        }
        final List<Method> methods = getterMethods(type);
        Function<Object, Mimic<?>> delegateBuilder = x -> {
            final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
            final HashMap<String, MimicType> typeMap = new HashMap<>();
            for (Method method : methods) {
                final String field = getterNameToFieldName(method.getName());
                final Object value = sneakyInvoker(x, method);
                final Class<?> rType = method.getReturnType();
                if (value == null) {
                    values.put(field, NULL.Null);
                    continue;
                }
                final MimicType mimicType = findFirstReverse(mimicTypes, t -> t.match(rType) || t.match(value));
                if (mimicType != null) {
                    values.put(field, mimicType.mimic(value));
                    typeMap.put(field, mimicType);
                    continue;
                }
                values.put(field, value);
            }
            return new Mimic<>(type, values, typeMap);
        };
        delegator.put(type, delegateBuilder);
        return (Mimic<T>) delegateBuilder.apply(instance);
    }

    /**
     * find possible interface of the class
     *
     * @param clazz target class (must not a container)
     * @return null|interface class
     */
    static Class<?> findInterface(Class<?> clazz) {
        if (reflectInterfaceCache.containsKey(clazz)) {
            final Class<?> aClass = reflectInterfaceCache.get(clazz);
            if (aClass.isAssignableFrom(NULL.class)) {
                return null;
            }
            return aClass;
        }
        final Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces == null || interfaces.length == 0
            || isCommonInterface(interfaces[0])
            || !interfaceNamePredicate.get().test(interfaces[0].getName())) {
            reflectInterfaceCache.put(clazz, NULL.class);
            return null;
        } else {//check if a full instance
            final List<String> lists = getterMethodsMapping(clazz, Method::getName, false);
            final List<String> list = getterMethodsMapping(interfaces[0], Method::getName, false);
            if (!list.containsAll(lists)) {
                reflectInterfaceCache.put(clazz, NULL.class);
                return null;
            } else {
                reflectInterfaceCache.put(clazz, interfaces[0]);
                return interfaces[0];
            }
        }
    }

    /**
     * convert a instance into Maybe Mimic.<br>
     * a {@link Mimic} will keep remains.<br>
     * a null will transform into a {@link NULL}.<br>
     * a {@link Proxy} will be treated as who him play as.<br>
     *
     * @param instance original instance
     * @return Maybe a Mimic
     */
    static Object autoMimic(Object instance) {
        if (instance == null) return NULL.Null;
        if (instance instanceof Mimic) return instance;
        if (staticMapping.containsKey(instance.getClass())) {
            return staticMapping.get(instance.getClass()).apply(instance);
        } else if (!predicateMapping.isEmpty()) {
            for (Predicate<Object> next : predicateMapping.keySet()) {
                if (next.test(instance)) return predicateMapping.get(next).apply(instance);
            }
        }
        final Object proxy = Delegator.tryRemoveProxy(instance);
        if (proxy instanceof Mimic) return proxy;
        final Class<?> aClass = instance.getClass();
        if (aClass.isPrimitive() || aClass.isEnum()) return instance;
        else {
            final Object result = containerProcess(instance, false, MimicUtil::autoMimic);
            if (result != instance) return result;
        }
        final Class<?> anInterface = findInterface(aClass);
        if (anInterface == null) return instance;
        return mimic(instance, anInterface);
    }

    /**
     * restore a Maybe Mimic to original form.<br>
     * a {@link Mimic} will keep remains.<br>
     * a null will transform into a {@link NULL}.<br>
     * a {@link Proxy} will be treated as who him play as.<br>
     *
     * @param instance the instance Maybe Mimic
     * @return original form of instance
     */
    static Object autoDisguise(Object instance) {
        if (instance instanceof NULL) return null;
        if (instance instanceof Delegator) {
            return ((Mimic<?>) instance).disguise();
        }
        final Object o = containerProcess(instance, true, MimicUtil::autoDisguise);
        if (o == null) return instance;
        return o;
    }


}
