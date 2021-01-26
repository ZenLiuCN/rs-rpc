package mimic;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
     * process common container
     *
     * @param instance  the instance
     * @param processor the processor
     * @return null or processor result
     */
    static Object containerProcess(Object instance, boolean realNull, Function<Object, Object> processor) {
        if (instance == null) return realNull ? null : NULL.Null;
        if (instance instanceof NULL) return realNull ? null : NULL.Null;
   /*     if (instance instanceof Optional) {
            Optional<Object> a = (Optional<Object>) instance;
            if (!a.isPresent()) return instance;
            return a.map(processor);
        } else if (instance instanceof List) {
            List<Object> a = (List<Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(processor).toList();
        } else if (instance instanceof Entry) {
            Entry<Object, Object> a = (Entry<Object, Object>) instance;
            return new AbstractMap.SimpleEntry<>(processor.apply(a.getKey()), processor.apply(a.getValue()));
        } else if (instance instanceof Map) {
            Map<Object, Object> a = (Map<Object, Object>) instance;
            if (a.isEmpty()) return instance;
            return Seq.seq(a).map(x -> x.map1(processor).map2(processor)).toMap(Tuple2::v1, Tuple2::v2);
        } else if (instance instanceof Tuple) {
            if (instance instanceof Tuple0) return instance;
            else if (instance instanceof Tuple1) return ((Tuple1) instance)
                .map1(processor);
            else if (instance instanceof Tuple2) return ((Tuple2) instance)
                .map1(processor)
                .map2(processor);
            else if (instance instanceof Tuple3) return ((Tuple3) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor);
            else if (instance instanceof Tuple4) return ((Tuple4) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor);
            else if (instance instanceof Tuple5) return ((Tuple5) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor);
            else if (instance instanceof Tuple6) return ((Tuple6) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor);
            else if (instance instanceof Tuple7) return ((Tuple7) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor);
            else if (instance instanceof Tuple8) return ((Tuple8) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor);
            else if (instance instanceof Tuple9) return ((Tuple9) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor);
            else if (instance instanceof Tuple10) return ((Tuple10) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor);
            else if (instance instanceof Tuple11) return ((Tuple11) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor);
            if (instance instanceof Tuple12) return ((Tuple12) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor)
                .map12(processor);
            else if (instance instanceof Tuple13) return ((Tuple13) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor)
                .map12(processor)
                .map13(processor);
            else if (instance instanceof Tuple14) return ((Tuple14) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor)
                .map12(processor)
                .map13(processor)
                .map14(processor);
            else if (instance instanceof Tuple15) return ((Tuple15) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor)
                .map12(processor)
                .map13(processor)
                .map14(processor)
                .map15(processor);
            else if (instance instanceof Tuple16) return ((Tuple16) instance)
                .map1(processor)
                .map2(processor)
                .map3(processor)
                .map4(processor)
                .map5(processor)
                .map6(processor)
                .map7(processor)
                .map8(processor)
                .map9(processor)
                .map10(processor)
                .map11(processor)
                .map12(processor)
                .map13(processor)
                .map14(processor)
                .map15(processor)
                .map16(processor);
            else return instance; }*/
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
        final List<Method> methods = getterMethods(type).toList();
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
                final MimicType mimicType = Seq.seq(mimicTypes).reverse()
                    .findFirst(t ->
                        t.match(rType) || t.match(value)
                    ).orElse(null);
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
        if (reflectInterfaceCache.containsKey(clazz)) return reflectInterfaceCache.get(clazz);
        final Class<?>[] interfaces = clazz.getInterfaces();
        if (interfaces == null || interfaces.length == 0
            || isCommonInterface(interfaces[0])
            || !interfaceNamePredicate.get().test(interfaces[0].getName())) {
            return null;
        } else {//check if a full instance
            final List<String> lists = getterMethods(clazz).map(Method::getName).collect(Collectors.toList());
            final long cnt = getterMethods(interfaces[0]).filter(x -> !lists.contains(x.getName())).count();
            if (cnt > 0) {
                reflectInterfaceCache.put(clazz, null);
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
