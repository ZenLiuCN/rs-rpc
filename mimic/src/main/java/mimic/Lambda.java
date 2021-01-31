package mimic;

import lombok.Getter;
import lombok.Setter;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public class Lambda<T> extends AbstractDelegator<T> {
    @Getter final Object value;
    @Getter final String methodName;
    @Getter @Setter private transient Invokable invoker;

    Lambda(Class<T> function, Invokable invoker, String methodName, boolean remote) {
        super(function);
        this.value = remote ? null : invoker.invokeWith();
        this.methodName = methodName;
        this.invoker = invoker;
    }


    @Override
    public Object get(String field) {
        throw new UnsupportedOperationException("this is a lambda delegator");
    }

    @Override
    public Delegator<T> set(String field, Object value) {
        throw new UnsupportedOperationException("this is a lambda delegator");
    }

    @Override
    public String dump() {
        return type + "$$Lambda$" + methodName + "{supply:" + value + "}";
    }

    @Override
    public String toString() {
        return dump();
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if (method.getName().equals(methodName)) {
            if (value != null) return value;
            return Objects.requireNonNull(invoker, "lambda mimic for " + type + " not set invoker!").invoke(args);
        }
        return commonHandler(method, args, args == null ? 0 : args.length, method.getName());
    }
}
