package mimic;

import lombok.extern.slf4j.Slf4j;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.*;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

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
        } else if (length == 0 && name.startsWith("get")) {
            final String field = name.substring(3);
            final Object v = NULL.restore(values.get(field));
            if (deep.containsKey(field)) {
                return deep.get(field).delegate.apply(v);
            } else {
                return NULL.restore(v);
            }
        } else if (length == 1 && name.startsWith("set")) {
            throw new IllegalAccessError("not support setter with mimic !");
            //return values.put(name.substring(3), args);
        } else if (length == 0 && name.equals("toString")) {
            return type + values.toString();
        } else if (method.isDefault()) {
            if (result == null) {
                getResult();
            }
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
