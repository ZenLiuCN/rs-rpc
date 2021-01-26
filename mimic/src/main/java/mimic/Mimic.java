package mimic;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static mimic.MimicType.mimicTypes;
import static mimic.ReflectUtil.CommonMethodName;
import static mimic.ReflectUtil.accessible;

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
public class Mimic<T> implements InvocationHandler, Delegator<T> {
    private final Class<T> type;
    private final ConcurrentHashMap<String, Object> values;
    private final HashMap<String, MimicType> deep;


    private Constructor<MethodHandles.Lookup> constructor;
    private transient Object[] result;

    private Object[] getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result;
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        int length = (args == null ? 0 : args.length);
        if (length == 0 && name.startsWith("is")) {
            return NULL.restore(values.get(name.substring(2)));
        } else if (length == 0 && name.startsWith("get") && !CommonMethodName.contains(name)) {
            final String field = name.substring(3);
            final Object v = NULL.restore(values.get(field));
            if (deep.containsKey(field)) {
                return deep.get(field).delegate(v);
            } else {
                return NULL.restore(v);
            }
        } else if (length == 0 && !CommonMethodName.contains(name)) {
            final Object v = NULL.restore(values.get(name));
            if (deep.containsKey(name)) {
                return deep.get(name).delegate(v);
            } else {
                return NULL.restore(v);
            }
        } else if (length == 1 && name.startsWith("set") && !CommonMethodName.contains(name)) {
            String field = name.substring(3);
            final Object v = args[0];
            setValue(field, v);
            return null;
        } else if (length == 1 && !CommonMethodName.contains(name)) {
            final Object v = args[0];
            setValue(name, v);
            return null;
        } else if (length == 0 && name.equals("toString")) {
            return type + "$Mimic" + values.toString();
        } else if (length == 1 && name.equals("equals")) {
            return this.equals(args[0]);
        } else if (length == 0 && name.equals("hashCode")) {
            return this.hashCode();
        } else if (length == 0 && name.equals("getClass")) {
            return type;
        } else if (length == 0 && name.equals("wait")) {
            this.wait();
            return null;
        } else if (length == 1 && name.equals("wait")) {
            this.wait((Long) args[0]);
            return null;
        } else if (length == 2 && name.equals("wait")) {
            this.wait((Long) args[0], (Integer) args[1]);
            return null;
        } else if (length == 0 && name.equals("notify")) {
            this.notify();
            return null;
        } else if (length == 0 && name.equals("notifyAll")) {
            this.notifyAll();
            return null;
        } else if (method.isDefault()) {
            getResult();
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

    private void setValue(String field, Object value) {
        if (value == null) {
            values.put(field, NULL.Null);
            return;
        }
        if (deep.containsKey(field)) {
            values.put(field, deep.get(field).mimic(value));
            return;
        }
        final MimicType mimicType = Seq.seq(mimicTypes)
            .findFirst(x -> x.match(value) || x.match(value.getClass())).orElse(null);
        final Object mimic = MimicUtil.autoMimic(value);
        values.put(field, mimic);
        if (mimicType != null) {
            deep.put(field, mimicType);
        }
    }

    Mimic(Class<T> type, ConcurrentHashMap<String, Object> values, HashMap<String, MimicType> deep) {
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
    public T disguise() {
        return type.cast(getResult()[0]);
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
        return type.equals(mimic.type) && values.equals(mimic.values) && deep.equals(mimic.deep);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, values, deep);
    }

    @Override
    public Mimic<T> set(String field, Object value) {
        if (field == null || field.isEmpty()) throw new IllegalArgumentException("field should never be null or empty");
        if (!Character.isUpperCase(field.charAt(0)))
            throw new IllegalArgumentException("field should be Pascal Case (first char also upper cased)");
        setValue(field, value);
        return this;
    }

    @Override
    public @Nullable Object get(String field) {
        return NULL.restore(values.get(field));
    }

    @Override
    public String dump() {
        return type + "$Mimic" + values + ":Ref" + deep;
    }
}
