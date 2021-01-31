package mimic;

import org.jooq.lambda.tuple.Tuple2;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static mimic.ReflectUtil.CommonMethodName;
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
abstract class BaseDelegator<T> extends AbstractDelegator<T> {
    private static final long serialVersionUID = -6499438977960561127L;
    protected final ConcurrentHashMap<String, Object> values;
    final static ConcurrentMap<String, Tuple2<Integer, String>> memo = buildSoftConcurrentCache();

    protected BaseDelegator(Class<T> type, ConcurrentHashMap<String, Object> values) {
        super(type);
        this.values = values;
    }

    @Override
    public BaseDelegator<T> set(String field, Object value) {
        if (field == null || field.isEmpty()) throw new IllegalArgumentException("field should never be null or empty");
        if (!Character.isUpperCase(field.charAt(0)))
            throw new IllegalArgumentException("field should be Pascal Case (first char also upper cased)");
        values.put(field, NULL.wrap(value));
        return this;
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
