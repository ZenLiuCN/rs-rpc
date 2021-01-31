package mimic;

import lombok.Getter;
import lombok.SneakyThrows;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import static mimic.ReflectUtil.accessible;

/**
 * Internal base processor
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
abstract class AbstractMimic<T> implements Mimic<T>, InvocationHandler {

    /**
     * Delegate target type
     */
    @Getter protected final Class<T> type;
    protected transient Object[] result;
    protected transient Constructor<MethodHandles.Lookup> constructor;

    protected AbstractMimic(Class<T> type) {
        this.type = type;
    }


    protected Object[] getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result;
    }


    /**
     * Create a Proxy from current Instance
     */
    @SuppressWarnings("unchecked")
    public T disguise() {
        return (T) getResult()[0];
    }

    @SneakyThrows
    protected final Object commonHandler(Method method, Object[] args, int length, String name) {
        if (length == 0 && name.equals("toString")) {
            return this.toString();
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
            try {
                if (constructor == null)
                    constructor = accessible(MethodHandles.Lookup.class.getDeclaredConstructor(Class.class, int.class));
                Class<?> declaringClass = method.getDeclaringClass();
                return constructor
                    .newInstance(declaringClass, MethodHandles.Lookup.PRIVATE)
                    .unreflectSpecial(method, declaringClass)
                    .bindTo(getResult()[0])
                    .invokeWithArguments(args);
            } catch (Throwable e) {
                throw new IllegalStateException("Cannot invoke default method", e);
            }
        } else {
            throw new IllegalStateException("not accepted call:" + name);
        }
    }


}
