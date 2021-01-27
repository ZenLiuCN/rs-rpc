package mimic;

import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static mimic.Lambda.*;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
final class internal {
    static final Map<Class<?>, Function<Object, Mimic<?>>> delegator = new ConcurrentHashMap<>();
    static final AtomicReference<Predicate<String>> interfaceNamePredicate = new AtomicReference<>(x -> true);
    static final List<Predicate<String>> commonInterfacePackagePredicate = Arrays.asList(
        x -> x.startsWith("java."),
        x -> x.startsWith("com.sun"),
        x -> x.startsWith("sun."),
        x -> x.startsWith("com.oracle"),
        x -> x.startsWith("jdk."),
        x -> x.startsWith("javax.")
    );
    static final List<Tuple2<Predicate<Object>, BiFunction<Function<Object, Object>, Object, Object>>>
        containerProcessors = Arrays.asList(
        tuple(x -> x instanceof Optional, (m, a) -> ((Optional<?>) a).map(m))
        , tuple(x -> x.getClass().isArray(), (m, a) -> ((Object[]) a).length == 0 ? a : fasterArray((Object[]) a, m))
        , tuple(x -> x instanceof List, (m, a) -> ((List<?>) a).isEmpty() ? a : fasterList((List<?>) a, m))
        , tuple(x -> x instanceof Map.Entry, (m, a) -> new AbstractMap.SimpleEntry<>(m.apply(((Map.Entry<?, ?>) a).getKey()), m.apply(((Map.Entry<?, ?>) a).getValue())))
        , tuple(x -> x instanceof Map, (m, a) -> ((Map<?, ?>) a).isEmpty() ? a : fasterMap((Map<?, ?>) a, m))
        , tuple(x -> x instanceof Tuple, (m, a) -> ((Tuple) a).degree() == 0 ? a : fasterTuple((Tuple) a, m))
    );


    static boolean isCommonInterface(Class<?> clazz) {
        return findFirst(commonInterfacePackagePredicate, x -> x.test(clazz.getCanonicalName())) != null;
    }

    static final AtomicReference<Predicate<Method>> getterPredicate = new AtomicReference<>(ReflectUtil::javaBeanGetterPredicate);
    static final AtomicBoolean fluent = new AtomicBoolean(false);
    static final Map<Class<?>, SoftReference<Method[]>> reflectMethodsCache = new ConcurrentHashMap<>();
    static final Map<Class<?>, SoftReference<List<Method>>> reflectGetterCache = new ConcurrentHashMap<>();
    static final Map<Class<?>, Class<?>> reflectInterfaceCache = new ConcurrentHashMap<>();
    static final Map<Class<?>, Function<Object, Object>> staticMapping = new ConcurrentHashMap<>();
    static final Map<Predicate<Object>, Function<Object, Object>> predicateMapping = new ConcurrentHashMap<>();
}
