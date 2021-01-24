package mimic;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.*;

import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static mimic.ReflectUtil.*;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public interface MimicUtil {

    AtomicReference<Predicate<String>> interfaceNamePredicate = new AtomicReference<>(x -> x.startsWith("com.medtreehealth"));

    /**
     * process common container
     *
     * @param instance  the instance
     * @param processor the processor
     * @return null or processor result
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static Object containerProcess(Object instance, Function<Object, Object> processor) {
        if (instance == null) return null;
        if (instance instanceof Optional) {
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
            else return instance;
        }
        return instance;
    }

    Map<Class<?>, Function<Object, Mimic<?>>> delegator = new ConcurrentHashMap<>();

    @SuppressWarnings("unchecked")
    static <T> Mimic<T> mimic(Object instance, Class<T> type) {
        if (delegator.containsKey(type)) {
            return (Mimic<T>) delegator.get(type).apply(instance);
        }
        final List<Method> methods = declaredGetterMethods(type).toList();
        Function<Object, Mimic<?>> delegateBuilder = x -> {
            final ConcurrentHashMap<String, Object> values = new ConcurrentHashMap<>();
            final HashMap<String, DeepType> typeMap = new HashMap<>();
            for (Method method : methods) {
                final String field = method.getName().startsWith("is") ? method.getName().substring(2) : method.getName().substring(3);
                final Object value = sneakyInvoker(x, method);
                final Class<?> rType = method.getReturnType();
                if (value == null) {
                    values.put(field, NULL.Null);
                    continue;
                }
                if (value instanceof Tuple) {
                    values.put(field, DeepType.TUPLE.mimic.apply(value));
                    typeMap.put(field, DeepType.TUPLE);
                    continue;
                }
                if (value instanceof Optional) {
                    values.put(field, DeepType.OPTIONAL.mimic.apply(value));
                    typeMap.put(field, DeepType.OPTIONAL);
                    continue;
                }
                if (value instanceof Map) {
                    values.put(field, DeepType.MAP.mimic.apply(value));
                    typeMap.put(field, DeepType.MAP);
                    continue;
                }
                if (value instanceof Entry) {
                    values.put(field, DeepType.ENTRY.mimic.apply(value));
                    typeMap.put(field, DeepType.ENTRY);
                    continue;
                }
                if (value instanceof List) {
                    values.put(field, DeepType.LIST.mimic.apply(value));
                    typeMap.put(field, DeepType.LIST);
                    continue;
                }
                if (!rType.isInterface()) {
                    values.put(field, value);//normal value
                    //typeMap.put(field, DeepType.UNK);
                } else {
                    values.put(field, DeepType.VALUE.mimic.apply(value));
                    typeMap.put(field, DeepType.VALUE);
                }
            }
            return new Mimic<>(type, values, typeMap);
        };
        delegator.put(type, delegateBuilder);
        return (Mimic<T>) delegateBuilder.apply(instance);
    }

    @SuppressWarnings("unchecked")
    enum DeepType {
        UNK(x -> x, x -> x),
        MAP(
            x -> x == null ? Collections.emptyMap() : Seq.seq((Map<Object, Object>) x).map(e -> e.map1(MimicUtil::autoMimic).map2(MimicUtil::autoMimic)).toMap(Tuple2::v1, Tuple2::v2),
            x -> x == null ? Collections.emptyMap() : Seq.seq((Map<Object, Object>) x).map(e -> e.map1(MimicUtil::autoDelegate).map2(MimicUtil::autoDelegate)).toMap(Tuple2::v1, Tuple2::v2)
        ),
        ENTRY(
            x -> x == null ? null : new AbstractMap.SimpleEntry<>(MimicUtil.autoMimic(((Entry<Object, Object>) x).getKey()), MimicUtil.autoMimic(((Entry<Object, Object>) x).getValue())),
            x -> x == null ? null : new AbstractMap.SimpleEntry<>(MimicUtil.autoDelegate(((Entry<Object, Object>) x).getKey()), MimicUtil.autoDelegate(((Entry<Object, Object>) x).getValue()))
        ),
        OPTIONAL(
            x -> x == null ? Optional.empty() : ((Optional<Object>) x).map(MimicUtil::autoMimic),
            x -> x == null ? Optional.empty() : ((Optional<Object>) x).map(MimicUtil::autoDelegate)
        ),
        LIST(
            x -> x == null ? Collections.emptyList() : Seq.seq((List<Object>) x).map(MimicUtil::autoMimic).toList(),
            x -> x == null ? Collections.emptyList() : Seq.seq((List<Object>) x).map(MimicUtil::autoDelegate).toList()
        ),
        TUPLE(
            x -> x == null ? null : tuple(Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoMimic).toArray()),
            x -> x == null ? null : tuple(Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoDelegate).toArray())
        ),
        VALUE(MimicUtil::autoMimic, MimicUtil::autoDelegate);
        public final Function<Object, Object> mimic;
        public final Function<Object, Object> delegate;

        DeepType(Function<Object, Object> mimic, Function<Object, Object> delegate) {
            this.mimic = mimic;
            this.delegate = delegate;
        }
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
        if (interfaces == null || interfaces.length == 0 || interfaces[0].getName().startsWith("java.") || !interfaceNamePredicate.get().test(interfaces[0].getName())) {
            return null;
        } else {//check if a full instance
            final List<String> lists = declaredGetterMethods(clazz).map(Method::getName).collect(Collectors.toList());
            final long cnt = declaredGetterMethods(interfaces[0]).filter(x -> !lists.contains(x.getName())).count();
            if (cnt > 0) {
                reflectInterfaceCache.put(clazz, null);
                return null;
            } else {
                reflectInterfaceCache.put(clazz, interfaces[0]);
                return interfaces[0];
            }
        }
    }


    static Object autoMimic(Object instance) {
        if (instance == null) return null;
        final Class<?> aClass = instance.getClass();
        if (aClass.isPrimitive() || aClass.isEnum() || aClass.isArray()) return instance;
        else {
            final Object result = containerProcess(instance, MimicUtil::autoMimic);
            if (result != instance) return result;
        }
        final Class<?> anInterface = findInterface(aClass);
        if (anInterface == null) return instance;
        return mimic(instance, anInterface);
    }


    static Object autoDelegate(Object instance) {
        if (instance == null) return null;
        if (instance instanceof Delegator) {
            return ((Mimic<?>) instance).delegate();
        }
        final Object o = containerProcess(instance, MimicUtil::autoDelegate);
        if (o == null) return instance;
        return o;
    }
}
