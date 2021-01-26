package mimic;

import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static mimic.MimicUtil.findInterface;
import static mimic.internal.isCommonInterface;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
@SuppressWarnings("unchecked")
public
enum DefaultMimicType implements MimicType {
    UNK(x -> true, x -> true, x -> x, x -> x),
    MAP(
        x -> x instanceof Map
        , Map.class::isAssignableFrom
        , x -> x == null ? Collections.emptyMap() : Seq.seq((Map<Object, Object>) x).map(e -> e.map1(MimicUtil::autoMimic).map2(MimicUtil::autoMimic)).toMap(Tuple2::v1, Tuple2::v2)
        , x -> x == null ? Collections.emptyMap() : Seq.seq((Map<Object, Object>) x).map(e -> e.map1(MimicUtil::autoDisguise).map2(MimicUtil::autoDisguise)).toMap(Tuple2::v1, Tuple2::v2)
    ),
    ENTRY(
        x -> x instanceof Map.Entry
        , Map.Entry.class::isAssignableFrom
        , x -> x == null ? null : new AbstractMap.SimpleEntry<>(MimicUtil.autoMimic(((Map.Entry<Object, Object>) x).getKey()), MimicUtil.autoMimic(((Map.Entry<Object, Object>) x).getValue()))
        , x -> x == null ? null : new AbstractMap.SimpleEntry<>(MimicUtil.autoDisguise(((Map.Entry<Object, Object>) x).getKey()), MimicUtil.autoDisguise(((Map.Entry<Object, Object>) x).getValue()))
    ),
    OPTIONAL(
        x -> x instanceof Optional
        , Optional.class::isAssignableFrom
        , x -> x == null ? Optional.empty() : ((Optional<Object>) x).map(MimicUtil::autoMimic)
        , x -> x == null ? Optional.empty() : ((Optional<Object>) x).map(MimicUtil::autoDisguise)
    ),
    LIST(
        x -> x instanceof List
        , List.class::isAssignableFrom
        , x -> x == null ? Collections.emptyList() : Seq.seq((List<Object>) x).map(MimicUtil::autoMimic).toList()
        , x -> x == null ? Collections.emptyList() : Seq.seq((List<Object>) x).map(MimicUtil::autoDisguise).toList()
    ),
    TUPLE(
        x -> x instanceof Tuple
        , Tuple.class::isAssignableFrom
        , x -> x == null ? null : tuple(Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoMimic).toArray()),
        x -> x == null ? null : tuple(Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoDisguise).toArray())
    ),
    ARRAY(
        x -> x.getClass().isArray()
        , Class::isArray
        , x -> x == null ? null : Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoMimic).toArray()
        , x -> x == null ? null : Seq.of(((Tuple) x).toArray()).map(MimicUtil::autoDisguise).toArray()
    ),
    VALUE(
        x -> findInterface(x.getClass()) != null
        , x -> (x.isInterface() && !isCommonInterface(x)) || findInterface(x) != null
        , MimicUtil::autoMimic, MimicUtil::autoDisguise);
    public final Predicate<Object> predicate;
    public final Predicate<Class<?>> typePredicate;
    public final Function<Object, Object> mimic;
    public final Function<Object, Object> delegate;

    DefaultMimicType(Predicate<Object> predicate, Predicate<Class<?>> typePredicate, Function<Object, Object> mimic, Function<Object, Object> delegate) {
        this.predicate = predicate;
        this.typePredicate = typePredicate;
        this.mimic = mimic;
        this.delegate = delegate;
    }

    @Override
    public boolean match(Object instance) {
        return predicate.test(instance);
    }

    @Override
    public boolean match(Class<?> type) {
        return typePredicate.test(type);
    }

    @Override
    public Object mimic(Object instance) {
        return mimic.apply(instance);
    }

    @Override
    public Object delegate(Object mimic) {
        return delegate.apply(mimic);
    }
}
