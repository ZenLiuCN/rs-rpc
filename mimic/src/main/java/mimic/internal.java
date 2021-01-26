package mimic;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.ref.SoftReference;
import java.lang.reflect.Method;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
final class internal {
    static final Map<Class<?>, Function<Object, Mimic<?>>> delegator = new ConcurrentHashMap<>();
    static final AtomicReference<Predicate<String>> interfaceNamePredicate = new AtomicReference<>(x -> true);
    static final List<Predicate<String>> commonInterfacePackagePredicate = Seq.<Predicate<String>>of(
        x -> x.startsWith("java."),
        x -> x.startsWith("com.sun"),
        x -> x.startsWith("sun."),
        x -> x.startsWith("com.oracle"),
        x -> x.startsWith("jdk."),
        x -> x.startsWith("javax.")
    ).toList();
    static final List<Tuple2<Predicate<Object>, BiFunction<Function<Object, Object>, Object, Object>>>
        containerProcessors = Seq.<Tuple2<Predicate<Object>, BiFunction<Function<Object, Object>, Object, Object>>>of(
        tuple(x -> x instanceof Optional, (m, a) -> ((Optional<?>) a).map(m))
        , tuple(x -> x instanceof List, (m, a) -> ((List<?>) a).isEmpty() ? a : Seq.seq((List<?>) a).map(m).toList())
        , tuple(x -> x instanceof Map.Entry, (m, a) -> new AbstractMap.SimpleEntry<>(m.apply(((Map.Entry<?, ?>) a).getKey()), m.apply(((Map.Entry<?, ?>) a).getValue())))
        , tuple(x -> x instanceof Map, (m, a) -> ((Map<?, ?>) a).isEmpty() ? a : Seq.seq(((Map<?, ?>) a)).map(x -> x.map1(m).map2(m)).toMap(Tuple2::v1, Tuple2::v2))
        , tuple(x -> x instanceof Tuple, (m, a) -> ((Tuple) a).degree() == 0 ? a : tuple(Seq.of(((Tuple) a).toArray()).map(m).toArray()))
    ).toList();

    static boolean isCommonInterface(Class<?> clazz) {
        return Seq.seq(commonInterfacePackagePredicate).anyMatch(x -> x.test(clazz.getCanonicalName()));
    }

    static final AtomicReference<Predicate<Method>> getterPredicate = new AtomicReference<>(ReflectUtil::javaBeanGetterPredicate);

    static final Map<Class<?>, SoftReference<Method[]>> reflectMethodsCache = new ConcurrentHashMap<>();
    static final Map<Class<?>, SoftReference<List<Method>>> reflectGetterCache = new ConcurrentHashMap<>();
    static final Map<Class<?>, Class<?>> reflectInterfaceCache = new ConcurrentHashMap<>();
}
