package mimic;

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static mimic.LambdaUtil.findFirst;
import static mimic.MimicType.mimicTypes;

/**
 * Mimic is a Nest Deep Delegator with only getters supported<br>
 * 1. support interface in getters <br>
 * 2. support interface in Map Value <br>
 * 3. support interface in List Value <br>
 * For Simple Value (means without Interface inside), use {@link MimicLight}.
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
public final class MimicDeep<T> extends BaseMimic<T> {
    private static final long serialVersionUID = -8454411289395784830L;
    private final ConcurrentHashMap<String, MimicType> ref = new ConcurrentHashMap<>();

    void setter(String field, Object value) {
        if (value == null) {
            values.put(field, NULL.Null);
            return;
        }
        if (ref.containsKey(field)) {
            values.put(field, ref.get(field).mimic(value));
            return;
        }
        final MimicType mimicType = findFirst(mimicTypes, x -> x.match(value) || x.match(value.getClass()));
        final Object mimic = MimicUtil.autoMimic(value);
        values.put(field, mimic);
        if (mimicType != null) {
            ref.put(field, mimicType);
        }
    }

    Object getter(String field) {
        if (ref.containsKey(field)) {
            return ref.get(field).disguise(NULL.restore(values.get(field)));
        }
        return NULL.restore(values.get(field));
    }


    MimicDeep(Class<T> type, ConcurrentHashMap<String, Object> values, ConcurrentHashMap<String, MimicType> ref) {
        super(type, values);
        this.ref.putAll(ref);
    }

    @Override
    public String toString() {
        return type + "$Mimic" + values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MimicDeep)) {
            final Object o1 = Mimic.tryRemoveProxy(o);
            if (!(o1 instanceof MimicDeep)) return false;
            return equals(o1);
        }
        MimicDeep<?> mimicDeep = (MimicDeep<?>) o;
        return type.equals(mimicDeep.type) && values.equals(mimicDeep.values) && ref.equals(mimicDeep.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, values, ref);
    }

    @Override
    public MimicDeep<T> set(String field, Object value) {
        validateField(field);
        setter(field, value);
        return this;
    }

    @Override
    public @Nullable Object get(String field) {
        return getter(field);
    }

    @Override
    public String dump() {
        return type + "$Mimic" + values + ":Ref" + ref;
    }
}
