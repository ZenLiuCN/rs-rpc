package mimic;

import lombok.SneakyThrows;
import org.jooq.lambda.tuple.Tuple2;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static mimic.ReflectUtil.CommonMethodName;
import static mimic.ReflectUtil.accessible;
import static mimic.internal.buildSoftConcurrentCache;
import static mimic.internal.fluent;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * Internal common processor
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-24
 */
abstract class BaseDelegator<T> implements Delegator<T>, InvocationHandler {
    private static final long serialVersionUID = -6499438977960561127L;
    /**
     * Delegate target type
     */
    protected final Class<T> type;
    protected final ConcurrentHashMap<String, Object> values;
    protected transient Constructor<MethodHandles.Lookup> constructor;
    protected transient Object[] result;
    final static ConcurrentMap<String, Tuple2<Integer, String>> memo = buildSoftConcurrentCache();


    protected BaseDelegator(Class<T> type, ConcurrentHashMap<String, Object> values) {
        this.type = type;
        this.values = values;
    }


    protected Object[] getResult() {
        if (result == null) {
            result = new Object[1];
            result[0] = java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class[]{type}, this);
        }
        return result;
    }

    @Override
    public BaseDelegator<T> set(String field, Object value) {
        if (field == null || field.isEmpty()) throw new IllegalArgumentException("field should never be null or empty");
        if (!Character.isUpperCase(field.charAt(0)))
            throw new IllegalArgumentException("field should be Pascal Case (first char also upper cased)");
        values.put(field, NULL.wrap(value));
        return this;
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

    protected final void validateField(String field) {
        if (field == null || field.isEmpty() || Character.isLowerCase(field.charAt(0)))
            throw new IllegalArgumentException("field should be as PascalCase!");
    }

    abstract Object getter(String field);

    abstract void setter(String field, Object value);

    static String tryDeGetter(String name) {
        if (name.startsWith("get")) return name.substring(3);
        if (name.startsWith("set")) return name.substring(3);
        if (name.startsWith("is")) return name.substring(2);
        return name;
    }

    protected Tuple2<Integer, String> methodDecide(String name, int length, Method method) {
        final String index = type.getCanonicalName() + '#' + name;
        if (memo.containsKey(index)) {
            return memo.get(index);
        }
        //a default calculate value
        if (method.isDefault() && method.getReturnType() != Void.TYPE && length == 0 && (values.containsKey(name) || values.containsKey(tryDeGetter(name)))) {
            final Tuple2<Integer, String> r = tuple(1, values.containsKey(name) ? name : tryDeGetter(name));
            memo.put(index, r);
            return r;
        }
        //normal default method
        if (method.isDefault()) {
            final Tuple2<Integer, String> r = tuple(-2, name);
            memo.put(index, r);
            return r;
        }
        //common method //todo hashCode should be store?
        if (CommonMethodName.contains(name)) {
            final Tuple2<Integer, String> r = tuple(-1, name);
            memo.put(index, r);
            return r;
        }
        //java bean getter
        if (length == 0 && name.startsWith("is") && method.getReturnType() != Void.TYPE && method.getReturnType() != type) {
            final Tuple2<Integer, String> r = tuple(1, name.substring(2));
            memo.put(index, r);
            return r;
        }
        if (length == 0 && name.startsWith("get") && method.getReturnType() != Void.TYPE && method.getReturnType() != type) {
            final Tuple2<Integer, String> r = tuple(1, name.substring(3));
            memo.put(index, r);
            return r;
        }
        //fluent getter
        if (fluent.get() && length == 0 && method.getReturnType() != Void.TYPE && method.getReturnType() != type) {
            final Tuple2<Integer, String> r = tuple(1, name);
            memo.put(index, r);
            return r;
        }
        //java bean setter
        if (length == 1 && name.startsWith("set") && method.getReturnType() == Void.TYPE) {
            final Tuple2<Integer, String> r = tuple(2, name.substring(3));
            memo.put(index, r);
            return r;
        }
        //chain setter
        if (length == 1 && name.startsWith("set") && method.getReturnType() == type) {
            final Tuple2<Integer, String> r = tuple(3, name.substring(3));
            memo.put(index, r);
            return r;
        }
        //fluent setter
        if (fluent.get() && length == 1 && method.getReturnType() == Void.TYPE) {
            final Tuple2<Integer, String> r = tuple(2, name);
            memo.put(index, r);
            return r;
        }
        //fluent chain setter
        if (fluent.get() && length == 1 && method.getReturnType() == type) {
            final Tuple2<Integer, String> r = tuple(3, name);
            memo.put(index, r);
            return r;
        }
        //other
        final Tuple2<Integer, String> r = tuple(-2, name);
        memo.put(index, r);
        return r;
    }

    @Override
    public final Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        String name = method.getName();
        int length = (args == null ? 0 : args.length);
        final Tuple2<Integer, String> decision = methodDecide(name, length, method);
        switch (decision.v1) {
            case 1:
                return getter(decision.v2);
            case 2:
                assert args != null;
                setter(decision.v2, args[0]);
                return null;
            case 3:
                assert args != null;
                setter(decision.v2, args[0]);
                return proxy;
            default:
                return commonHandler(method, args, length, name);
        }

    }
}
