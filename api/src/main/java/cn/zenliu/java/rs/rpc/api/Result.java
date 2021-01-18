package cn.zenliu.java.rs.rpc.api;

import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Result value means there is no Error throws by a method or function which product it.<br>
 * Result can with both or none of result and error.<br>
 * Result self should never be null.<br>
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-17
 */
public interface Result<T> {
    Result<Void> NOTHING = new ResultNothingImpl<>();

    static <T> Result<T> ok(T data) {
        return new ResultOkImpl<T>(data);
    }

    static <T> Result<T> ok(T data, Throwable err) {
        return data == null ? error(err) : err == null ? ok(data) : new ResultImpl<>(data, err);
    }

    static <T> Result<T> error(Throwable e) {
        return new ResultErrorImpl<T>(e);
    }

    @SuppressWarnings("unchecked")
    static <T> Result<T> nothing() {
        return (Result<T>) NOTHING;
    }

    static <T> Result<T> wrap(@NotNull Supplier<T> supplier) {
        try {
            return Result.ok(supplier.get());
        } catch (Throwable e) {
            return Result.error(e);
        }
    }

    static <T> Result<T> wrap(@NotNull Supplier<Optional<T>> supplier, @NotNull Supplier<Throwable> onEmpty) {
        try {
            return Result.ok(supplier.get().orElseThrow(onEmpty));
        } catch (Throwable e) {
            return Result.error(e);
        }
    }

    @Nullable Throwable getError();

    @Nullable T getResult();

    default boolean hasError() {
        return getError() != null;
    }

    default boolean hasResult() {
        return getResult() != null;
    }

    /**
     * ignore error, get result as Optional.
     */
    default Optional<T> asOptional() {
        return Optional.ofNullable(getResult());
    }

    /**
     * if have error ,then throw it else get a Optional result
     */
    default Optional<T> asOptionalThrow() {
        if (getError() != null) throw (RuntimeException) getError();
        return asOptional();
    }

    /**
     * if have error,throw it ; else get the result(maybe null)
     */
    default @Nullable T getOrThrow() {
        if (getError() != null) throw (RuntimeException) getError();
        return getResult();
    }

    /**
     * just action like {@link Optional#map(Function)}
     */
    @SuppressWarnings("unchecked")
    default <R> Result<R> map(@NotNull Function<T, R> mapping) {
        if (hasResult()) return Result.ok(mapping.apply(getResult()), getError());
        return (Result<R>) this;
    }

    default <R> Result<R> map(@NotNull Function<T, R> mapping, @NotNull Consumer<Throwable> errorConsumer) {
        if (hasError()) errorConsumer.accept(getError());
        return map(mapping);
    }

    @ToString
    final class ResultImpl<T> implements Result<T> {
        @Getter final Throwable error;
        @Getter final T result;

        ResultImpl(T result, Throwable error) {
            this.error = error;
            this.result = result;
        }
    }

    @ToString
    final class ResultErrorImpl<T> implements Result<T> {
        @Getter final Throwable error;

        ResultErrorImpl(Throwable error) {
            this.error = error;

        }

        @Override
        public @Nullable T getResult() {
            return null;
        }

        @Override
        public boolean hasError() {
            return true;
        }

        @Override
        public boolean hasResult() {
            return false;
        }

        @Override
        public Optional<T> asOptional() {
            return Optional.empty();
        }

        @Override
        public Optional<T> asOptionalThrow() {
            throw (RuntimeException) error;
        }

        @Override
        public @Nullable T getOrThrow() {
            throw (RuntimeException) error;
        }
    }

    @ToString
    final class ResultOkImpl<T> implements Result<T> {
        @Getter final T result;

        ResultOkImpl(T result) {
            this.result = result;
        }

        @Override
        public @Nullable Throwable getError() {
            return null;
        }

        @Override
        public boolean hasResult() {
            return true;
        }

        @Override
        public boolean hasError() {
            return false;
        }

        @Override
        public Optional<T> asOptional() {
            return Optional.ofNullable(result);
        }

        @Override
        public Optional<T> asOptionalThrow() {
            return asOptional();
        }

        @Override
        public @Nullable T getOrThrow() {
            return result;
        }
    }

    @ToString
    final class ResultNothingImpl<T> implements Result<T> {

        @Override
        public @Nullable Throwable getError() {
            return null;
        }

        @Override
        public @Nullable T getResult() {
            return null;
        }
    }
}
