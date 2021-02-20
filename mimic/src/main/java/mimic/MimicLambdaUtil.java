package mimic;

import cn.zenliu.java.mimic.api.MimicApi;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple3;
import org.jooq.lambda.tuple.Tuple4;

import java.lang.reflect.Method;
import java.util.function.*;

import static mimic.MimicLambdaUtil.internal.jvmWrapLambda;
import static mimic.MimicLambdaUtil.internal.reflectWarpLambda;
import static org.jooq.lambda.tuple.Tuple.tuple;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public interface MimicLambdaUtil {
    static @Nullable Tuple4<
        Boolean,//call remote if false ,should call locally (such as Supplier)
        Invokable,
        Class<?>,//the functional interface
        String//the method Name
        > prepareLambda(Object arg) {
        if (arg == null || !isLambda(arg)) return null;
        final Tuple4<Boolean, Invokable, Class<?>, String> tuple = jvmWrapLambda(arg);
        return tuple == null ? reflectWarpLambda(arg) : tuple;
    }


    static boolean isLambda(Object arg) {
        return arg.getClass().toString().contains("$$Lambda$");
    }

    final class internal {
        static Cache<Class<?>, Tuple3<Boolean, Class<?>, Method>> cache = MimicApi.buildCache(null, true);

        static Class<?> findFunctionalInterface(Class<?> some) {
            final Class<?>[] interfaces = some.getInterfaces();
            for (Class<?> anInterface : interfaces) {
                if (anInterface.getAnnotation(FunctionalInterface.class) != null) return anInterface;
            }
            return null;
        }

        static boolean isJavaLambda(Object arg) {
            if (arg instanceof BiConsumer) return true;
            if (arg instanceof BiFunction) return true;
            //if(arg instanceof BinaryOperator) return true;
            if (arg instanceof BiPredicate) return true;
            if (arg instanceof BooleanSupplier) return true;
            if (arg instanceof Consumer) return true;
            if (arg instanceof DoubleBinaryOperator) return true;
            if (arg instanceof DoubleConsumer) return true;
            if (arg instanceof DoubleFunction) return true;
            if (arg instanceof DoublePredicate) return true;
            if (arg instanceof DoubleSupplier) return true;
            if (arg instanceof DoubleToIntFunction) return true;
            if (arg instanceof DoubleToLongFunction) return true;
            if (arg instanceof DoubleUnaryOperator) return true;
            if (arg instanceof Function) return true;
            if (arg instanceof IntBinaryOperator) return true;
            if (arg instanceof IntConsumer) return true;
            if (arg instanceof IntFunction) return true;
            if (arg instanceof IntPredicate) return true;
            if (arg instanceof IntSupplier) return true;
            if (arg instanceof IntToDoubleFunction) return true;
            if (arg instanceof IntToLongFunction) return true;
            if (arg instanceof IntUnaryOperator) return true;
            if (arg instanceof LongBinaryOperator) return true;
            if (arg instanceof LongConsumer) return true;
            if (arg instanceof LongFunction) return true;
            if (arg instanceof LongPredicate) return true;
            if (arg instanceof LongSupplier) return true;
            if (arg instanceof LongToDoubleFunction) return true;
            if (arg instanceof LongToIntFunction) return true;
            if (arg instanceof LongUnaryOperator) return true;
            if (arg instanceof ObjDoubleConsumer) return true;
            if (arg instanceof ObjIntConsumer) return true;
            if (arg instanceof ObjLongConsumer) return true;
            if (arg instanceof Predicate) return true;
            if (arg instanceof Supplier) return true;
            if (arg instanceof ToDoubleBiFunction) return true;
            if (arg instanceof ToDoubleFunction) return true;
            if (arg instanceof ToIntBiFunction) return true;
            if (arg instanceof ToIntFunction) return true;
            if (arg instanceof ToLongBiFunction) return true;
            return arg instanceof ToLongFunction;
            // if(arg instanceof UnaryOperator) return true;
        }

        static boolean isReflectLambda(Object arg) {
            if (cache.containsKey(arg.getClass())) {
                return cache.get(arg.getClass()).v1;
            } else {
                final Method[] declaredMethods = arg.getClass().getDeclaredMethods();
                final boolean isLambda = declaredMethods.length == 1 || (declaredMethods.length == 2 && declaredMethods[1].getName().equals("writeReplace"));
                if (isLambda) {
                    cache.put(arg.getClass(), tuple(true, findFunctionalInterface(arg.getClass()), declaredMethods[0]));
                } else {
                    cache.put(arg.getClass(), tuple(false, null, null));
                }
                return isLambda;
            }
        }

        static Tuple4<
            Boolean,//call remote if false ,should call locally (such as Supplier)
            Invokable,
            Class<?>,//the functional interface
            String//the method Name
            > reflectWarpLambda(Object arg) {
            final boolean b = isReflectLambda(arg);
            if (!b) return null;
            final Tuple3<Boolean, Class<?>, Method> m = cache.get(arg.getClass());
            final Method method = m.v3;

            return tuple(method.getParameterCount() != 0, //if accept something that is not a locally lambda
                x -> {
                    if (method.getParameterCount() > 0 && (x == null || x.length != method.getParameterCount()))
                        throw new IllegalArgumentException("argument length not match");
                    try {
                        return method.invoke(arg, x);
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                },
                m.v2, m.v3.getName());
        }

        @SuppressWarnings("unchecked")
        static Tuple4<
            Boolean,//call remote if false ,should call locally (such as Supplier)
            Invokable,
            Class<?>,//the functional interface
            String//the method Name
            > wrapLambda(int length, Object invoke, Class<?> cls, String name) {
            if (invoke instanceof Function) {
                final Function<Object[], Object> inv = (Function<Object[], Object>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    return inv.apply(args);
                }, cls, name);
            }
            if (invoke instanceof Predicate) {
                final Predicate<Object[]> inv = (Predicate<Object[]>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    return inv.test(args);
                }, cls, name);
            }
            if (invoke instanceof Supplier) {
                final Supplier<Object> inv = (Supplier<Object>) invoke;
                return tuple(false, args -> inv.get(), cls, name);
            }
            if (invoke instanceof Consumer) {
                final Consumer<Object[]> inv = (Consumer<Object[]>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    inv.accept(args);
                    return null;
                }, cls, name);
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        static Tuple4<
            Boolean,//call remote if false ,should call locally (such as Supplier)
            Invokable,
            Class<?>,//the functional interface
            String//the method Name
            > jvmWrapLambda(Object argument) {
            //@formatter:off
            if(argument instanceof BinaryOperator) return wrapLambda(2,wrapBinaryOperator((BinaryOperator<Object>) argument),BinaryOperator.class,"apply");
            if(argument instanceof BiConsumer) return wrapLambda(2,wrapBiConsumer((BiConsumer<Object, Object>) argument),BiConsumer.class,"accept");
            if(argument instanceof BiFunction) return wrapLambda(2,wrapBiFunction((BiFunction<Object, Object, Object>) argument),BiFunction.class,"apply");
            if(argument instanceof BiPredicate) return wrapLambda(2,wrapBiPredicate((BiPredicate<Object, Object>) argument),BiPredicate.class,"test");
            if(argument instanceof BooleanSupplier) return wrapLambda(0,wrapBooleanSupplier((BooleanSupplier) argument),BooleanSupplier.class,"getAsBoolean");
            if(argument instanceof Consumer) return wrapLambda(1,wrapConsumer((Consumer<Object>) argument),Consumer.class,"accept");
            if(argument instanceof DoubleBinaryOperator) return wrapLambda(2,wrapDoubleBinaryOperator((DoubleBinaryOperator) argument),DoubleBinaryOperator.class,"applyAsDouble");
            if(argument instanceof DoubleConsumer) return wrapLambda(1,wrapDoubleConsumer((DoubleConsumer) argument),DoubleConsumer.class,"accept");
            if(argument instanceof DoubleFunction) return wrapLambda(2,wrapDoubleFunction((DoubleFunction<Object>) argument),DoubleFunction.class,"apply");
            if(argument instanceof DoublePredicate) return wrapLambda(1,wrapDoublePredicate((DoublePredicate) argument),DoublePredicate.class,"test");
            if(argument instanceof DoubleSupplier) return wrapLambda(0,wrapDoubleSupplier((DoubleSupplier) argument),DoubleSupplier.class,"getAsDouble");
            if(argument instanceof DoubleToIntFunction) return wrapLambda(1,wrapDoubleToIntFunction((DoubleToIntFunction) argument),DoubleToIntFunction.class,"applyAsInt");
            if(argument instanceof DoubleToLongFunction) return wrapLambda(1,wrapDoubleToLongFunction((DoubleToLongFunction) argument),DoubleToLongFunction.class,"applyAsLong");
            if(argument instanceof DoubleUnaryOperator) return wrapLambda(1,wrapDoubleUnaryOperator((DoubleUnaryOperator) argument),DoubleUnaryOperator.class,"applyAsDouble");
            if(argument instanceof UnaryOperator) return wrapLambda(1,wrapUnaryOperator((UnaryOperator<Object>) argument),UnaryOperator.class,"apply");
            if(argument instanceof Function) return wrapLambda(1,wrapFunction((Function<Object, Object>) argument),Function.class,"apply");
            if(argument instanceof IntBinaryOperator) return wrapLambda(2,wrapIntBinaryOperator((IntBinaryOperator) argument),IntBinaryOperator.class,"applyAsInt");
            if(argument instanceof IntConsumer) return wrapLambda(1,wrapIntConsumer((IntConsumer) argument),IntConsumer.class,"accept");
            if(argument instanceof IntFunction) return wrapLambda(1,wrapIntFunction((IntFunction<Object>) argument),IntFunction.class,"apply");
            if(argument instanceof IntPredicate) return wrapLambda(1,wrapIntPredicate((IntPredicate) argument),IntPredicate.class,"test");
            if(argument instanceof IntSupplier) return wrapLambda(0,wrapIntSupplier((IntSupplier) argument),IntSupplier.class,"getAsInt");
            if(argument instanceof IntToDoubleFunction) return wrapLambda(1,wrapIntToDoubleFunction((IntToDoubleFunction) argument),IntToDoubleFunction.class,"applyAsDouble");
            if(argument instanceof IntToLongFunction) return wrapLambda(1,wrapIntToLongFunction((IntToLongFunction) argument),IntToLongFunction.class,"applyAsLong");
            if(argument instanceof IntUnaryOperator) return wrapLambda(1,wrapIntUnaryOperator((IntUnaryOperator) argument),IntUnaryOperator.class,"applyAsInt");
            if(argument instanceof LongBinaryOperator) return wrapLambda(2,wrapLongBinaryOperator((LongBinaryOperator) argument),LongBinaryOperator.class,"applyAsLong");
            if(argument instanceof LongConsumer) return wrapLambda(1,wrapLongConsumer((LongConsumer) argument),LongConsumer.class,"accept");
            if(argument instanceof LongFunction) return wrapLambda(1,wrapLongFunction((LongFunction<Object>) argument),LongFunction.class,"apply");
            if(argument instanceof LongPredicate) return wrapLambda(1,wrapLongPredicate((LongPredicate) argument),LongPredicate.class,"test");
            if(argument instanceof LongSupplier) return wrapLambda(0,wrapLongSupplier((LongSupplier) argument),LongSupplier.class,"getAsLong");
            if(argument instanceof LongToDoubleFunction) return wrapLambda(1,wrapLongToDoubleFunction((LongToDoubleFunction) argument),LongToDoubleFunction.class,"applyAsDouble");
            if(argument instanceof LongToIntFunction) return wrapLambda(1,wrapLongToIntFunction((LongToIntFunction) argument),LongToIntFunction.class,"applyAsInt");
            if(argument instanceof LongUnaryOperator) return wrapLambda(1,wrapLongUnaryOperator((LongUnaryOperator) argument),LongUnaryOperator.class,"applyAsLong");
            if(argument instanceof ObjDoubleConsumer) return wrapLambda(2,wrapObjDoubleConsumer((ObjDoubleConsumer<Object>) argument),ObjDoubleConsumer.class,"accept");
            if(argument instanceof ObjIntConsumer) return wrapLambda(2,wrapObjIntConsumer((ObjIntConsumer<Object>) argument),ObjIntConsumer.class,"accept");
            if(argument instanceof ObjLongConsumer) return wrapLambda(2,wrapObjLongConsumer((ObjLongConsumer<Object>) argument),ObjLongConsumer.class,"accept");
            if(argument instanceof Predicate) return wrapLambda(1,wrapPredicate((Predicate<Object>) argument),Predicate.class,"test");
            if(argument instanceof Supplier) return wrapLambda(0,wrapSupplier((Supplier<Object>) argument),Supplier.class,"get");
            if(argument instanceof ToDoubleBiFunction) return wrapLambda(2,wrapToDoubleBiFunction((ToDoubleBiFunction<Object, Object>) argument),ToDoubleBiFunction.class,"applyAsDouble");
            if(argument instanceof ToDoubleFunction) return wrapLambda(1,wrapToDoubleFunction((ToDoubleFunction<Object>) argument),ToDoubleFunction.class,"applyAsDouble");
            if(argument instanceof ToIntBiFunction) return wrapLambda(2,wrapToIntBiFunction((ToIntBiFunction<Object, Object>) argument),ToIntBiFunction.class,"applyAsInt");
            if(argument instanceof ToIntFunction) return wrapLambda(1,wrapToIntFunction((ToIntFunction<Object>) argument),ToIntFunction.class,"applyAsInt");
            if(argument instanceof ToLongBiFunction) return wrapLambda(2,wrapToLongBiFunction((ToLongBiFunction<Object, Object>) argument),ToLongBiFunction.class,"applyAsLong");
            if(argument instanceof ToLongFunction) return wrapLambda(1,wrapToLongFunction((ToLongFunction<Object>) argument),ToLongFunction.class,"applyAsLong");
            //@formatter:on
            return null;
        }

        //@formatter:off
        static     Consumer<Object[]> wrapBiConsumer(BiConsumer<Object,Object> f){return o->f.accept(o[0],o[1]);}
        static     Consumer<Object[]> wrapConsumer(Consumer<Object> f){return o->f.accept(o[0]);}
        static     Consumer<Object[]> wrapIntConsumer( IntConsumer f){return o->f.accept((Integer) o[0]);}
        static     Consumer<Object[]> wrapLongConsumer(  LongConsumer f){return o->f.accept((Long) o[0]);}
        static     Consumer<Object[]> wrapDoubleConsumer( DoubleConsumer f){return o->f.accept((Double) o[0]);}
        static     Consumer<Object[]> wrapObjDoubleConsumer( ObjDoubleConsumer<Object> f){return o->f.accept(o[0], (Double) o[1]);}
        static     Consumer<Object[]> wrapObjIntConsumer (ObjIntConsumer <Object> f){return o->f.accept(o[0], (Integer) o[1]);}
        static     Consumer<Object[]> wrapObjLongConsumer(  ObjLongConsumer<Object> f){return o->f.accept(o[0], (Long) o[1]);}
        static     Predicate<Object[]> wrapBiPredicate( BiPredicate<Object,Object> f){return o->f.test(o[0],o[1]);}
        static     Predicate<Object[]> wrapDoublePredicate( DoublePredicate f){return o->f.test((Double) o[0]);}
        static     Predicate<Object[]> wrapIntPredicate( IntPredicate f){return o->f.test((Integer) o[0]);}
        static     Predicate<Object[]> wrapLongPredicate( LongPredicate f){return o->f.test((Long) o[0]);}
        static     Predicate<Object[]> wrapPredicate( Predicate<Object> f){return o->f.test(o[0]);}
        static     Supplier<Object> wrapBooleanSupplier(BooleanSupplier f){return f::getAsBoolean;}
        static     Supplier<Object> wrapDoubleSupplier(DoubleSupplier f){return f::getAsDouble;}
        static     Supplier<Object> wrapIntSupplier(IntSupplier f){return f::getAsInt;}
        static     Supplier<Object> wrapLongSupplier(LongSupplier f){return f::getAsLong;}
        static     Supplier<Object> wrapSupplier(Supplier<Object> f){return f;}
        static     Function<Object[],Object> wrapBiFunction(BiFunction<Object,Object,Object> f){return o->f.apply(o[0],o[1]);}
        static     Function<Object[],Object> wrapBinaryOperator(BinaryOperator<Object> f){return o->f.apply(o[0],o[1]);}
        static     Function<Object[],Object> wrapDoubleBinaryOperator(DoubleBinaryOperator f){return o->f.applyAsDouble((Double)o[0],(Double)o[1]);}
        static     Function<Object[],Object> wrapDoubleFunction(DoubleFunction<Object> f){return o->f.apply((Double) o[0]);}
        static     Function<Object[],Object> wrapDoubleToIntFunction(DoubleToIntFunction f){return o->f.applyAsInt((Double) o[0]);}
        static     Function<Object[],Object> wrapDoubleToLongFunction(DoubleToLongFunction f){return o->f.applyAsLong((Double) o[0]);}
        static     Function<Object[],Object> wrapDoubleUnaryOperator(DoubleUnaryOperator f){return o->f.applyAsDouble((Double) o[0]);}
        static     Function<Object[],Object> wrapFunction(Function<Object,Object> f){return o->f.apply(o[0]);}
        static     Function<Object[],Object> wrapIntBinaryOperator(IntBinaryOperator f){return o->f.applyAsInt((Integer)o[0],(Integer)o[1]);}
        static     Function<Object[],Object> wrapIntFunction(IntFunction<Object> f){return o->f.apply((Integer) o[0]);}
        static     Function<Object[],Object> wrapIntToDoubleFunction(IntToDoubleFunction f){return o->f.applyAsDouble((Integer) o[0]);}
        static     Function<Object[],Object> wrapIntToLongFunction(IntToLongFunction f){return o->f.applyAsLong((Integer) o[0]);}
        static     Function<Object[],Object> wrapIntUnaryOperator(IntUnaryOperator f){return o->f.applyAsInt((Integer) o[0]);}
        static     Function<Object[],Object> wrapLongBinaryOperator(LongBinaryOperator f){return o->f.applyAsLong((Long)o[0],(Long)o[1]);}
        static     Function<Object[],Object> wrapLongFunction(LongFunction<Object> f){return o->f.apply((Long) o[0]);}
        static     Function<Object[],Object> wrapLongToDoubleFunction(LongToDoubleFunction f){return o->f.applyAsDouble((Long) o[0]);}
        static     Function<Object[],Object> wrapLongToIntFunction(LongToIntFunction f){return o->f.applyAsInt((Long) o[0]);}
        static     Function<Object[],Object> wrapLongUnaryOperator(LongUnaryOperator f){return o->f.applyAsLong((Long) o[0]);}
        static     Function<Object[],Object> wrapToDoubleBiFunction(ToDoubleBiFunction<Object,Object> f){return o->f.applyAsDouble(o[0],o[1]);}
        static     Function<Object[],Object> wrapToDoubleFunction(ToDoubleFunction<Object> f){return o->f.applyAsDouble(o[0]);}
        static     Function<Object[],Object> wrapToIntBiFunction(ToIntBiFunction<Object,Object> f){return o->f.applyAsInt(o[0],o[1]);}
        static     Function<Object[],Object> wrapToIntFunction(ToIntFunction<Object> f){return o->f.applyAsInt(o[0]);}
        static     Function<Object[],Object> wrapToLongBiFunction(ToLongBiFunction<Object,Object> f){return o->f.applyAsLong(o[0],o[1]);}
        static     Function<Object[],Object> wrapToLongFunction(ToLongFunction<Object> f){return o->f.applyAsLong(o[0]);}
        static     Function<Object[],Object> wrapUnaryOperator(UnaryOperator<Object> f){return o->f.apply(o[0]);}
        //@formatter:on
    }

}
