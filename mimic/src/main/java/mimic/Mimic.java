package mimic;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static mimic.Lambda.findFirst;
import static mimic.MimicType.mimicTypes;

/**
 * Mimic is a Nest Deep Delegator with only getters supported<br>
 * 1. support interface in getters <br>
 * 2. support interface in Map Value <br>
 * 3. support interface in List Value <br>
 * For Simple Value (means without Interface inside), use {@link Proxy}.
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
@Slf4j
public class Mimic<T> extends BaseDelegator<T> {
    private final HashMap<String, MimicType> ref;


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

    Mimic(Class<T> type, ConcurrentHashMap<String, Object> values, HashMap<String, MimicType> ref) {
        super(type, values);
        this.ref = ref;
    }

    @Override
    public String toString() {
        return type + "$Mimic" + values;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Mimic)) {
            final Object o1 = Delegator.tryRemoveProxy(o);
            if (!(o1 instanceof Mimic)) return false;
            return equals(o1);
        }
        Mimic<?> mimic = (Mimic<?>) o;
        return type.equals(mimic.type) && values.equals(mimic.values) && ref.equals(mimic.ref);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, values, ref);
    }

    @Override
    public Mimic<T> set(String field, Object value) {
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
