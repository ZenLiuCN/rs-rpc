package mimic;

import lombok.Getter;

import java.lang.reflect.Method;
import java.util.Objects;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public class MimicLambda<T> extends AbstractMimic<T> {
    @Getter final Object value;
    @Getter final String methodName;
    @Getter private transient Invokable invoker;

    MimicLambda(Class<T> function, Invokable invoker, String methodName, boolean remote) {
        super(function);
        this.value = remote ? null : invoker.invokeWith();
        this.methodName = methodName;
        this.invoker = invoker;
    }

    public boolean isSupplier() {
        return value != null;
    }

    public MimicLambda<T> setInvoker(Invokable invoker) {
        if (value != null) throw new IllegalStateException("set invoker of a Supplier not allowed");
        this.invoker = invoker;
        return this;
    }

    @Override
    public Object get(String field) {
        throw new UnsupportedOperationException("this is a lambda delegator");
    }

    @Override
    public Mimic<T> set(String field, Object value) {
        throw new UnsupportedOperationException("this is a lambda delegator");
    }

    @Override
    public String dump() {
        return type + "$$Lambda$Mimic$" + methodName + "{supply:" + value + "}";
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
