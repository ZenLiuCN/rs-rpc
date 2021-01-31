package mimic;

import org.jooq.lambda.tuple.Tuple;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

import static mimic.LambdaUtil.*;
import static mimic.MimicUtil.findInterface;
import static mimic.internal.isCommonInterface;

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
        , x -> x == null ? Collections.emptyMap() : fasterMap((Map<?, ?>) x, MimicUtil::autoMimic)
        , x -> x == null ? Collections.emptyMap() : fasterMap((Map<?, ?>) x, MimicUtil::autoDisguise)
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
        , x -> x == null ? Collections.emptyList() : fasterList((List<?>) x, MimicUtil::autoMimic)
        , x -> x == null ? Collections.emptyList() : fasterList((List<?>) x, MimicUtil::autoDisguise)
    ),
    TUPLE(
        x -> x instanceof Tuple
        , Tuple.class::isAssignableFrom
        , x -> x == null ? null : fasterTuple((Tuple) x, MimicUtil::autoMimic),
        x -> x == null ? null : fasterTuple((Tuple) x, MimicUtil::autoDisguise)
    ),
    ARRAY(
        x -> x.getClass().isArray()
        , Class::isArray
        , x -> x == null ? null : fasterArray((Object[]) x, MimicUtil::autoMimic)
        , x -> x == null ? null : fasterArray((Object[]) x, MimicUtil::autoDisguise)
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
    public Object disguise(Object mimic) {
        return delegate.apply(mimic);
    }
}
