package mimic;

import lombok.extern.slf4j.Slf4j;
import org.jooq.lambda.tuple.Tuple;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static mimic.ReflectUtil.CommonMethodName;

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
    private final HashMap<String, MimicUtil.DeepType> deep;


    private Constructor<MethodHandles.Lookup> constructor;
    private transient Object[] result;

    private Object[] getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result;
    }

    static <T extends AccessibleObject> T accessible(T accessible) {
        if (accessible == null) {
            return null;
        }
        if (accessible instanceof Member) {
            Member member = (Member) accessible;
            if (Modifier.isPublic(member.getModifiers()) &&
                Modifier.isPublic(member.getDeclaringClass().getModifiers())) {
                return accessible;
            }
        }
        if (!accessible.isAccessible())
            accessible.setAccessible(true);
        return accessible;
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
                return deep.get(field).delegate.apply(v);
            } else {
                return NULL.restore(v);
            }
        } else if (length == 0 && !CommonMethodName.contains(name)) {
            final Object v = NULL.restore(values.get(name));
            if (deep.containsKey(name)) {
                return deep.get(name).delegate.apply(v);
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
            return type + values.toString();
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
        final Object mimic = MimicUtil.autoMimic(value);
        values.put(field, mimic);
        if (mimic instanceof Mimic) {
            if (value instanceof List) {
                deep.put(field, MimicUtil.DeepType.LIST);
            } else if (value instanceof Map.Entry) {
                deep.put(field, MimicUtil.DeepType.ENTRY);
            } else if (value instanceof Tuple) {
                deep.put(field, MimicUtil.DeepType.TUPLE);
            } else if (value instanceof Optional) {
                deep.put(field, MimicUtil.DeepType.OPTIONAL);
            } else if (value instanceof Map) {
                deep.put(field, MimicUtil.DeepType.MAP);
            } else {
                deep.put(field, MimicUtil.DeepType.VALUE);
            }
        }
    }

    Mimic(Class<T> type, ConcurrentHashMap<String, Object> values, HashMap<String, MimicUtil.DeepType> deep) {
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
    public T delegate() {
        return type.cast(getResult()[0]);
    }


}
