package cn.zenliu.java.mimic.test;

import cn.zenliu.java.mimic.api.MimicApi;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.function.Function3;
import org.jooq.lambda.tuple.Tuple2;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.function.*;

import static cn.zenliu.java.mimic.test.LambdaMimic.internal.jvmWrapLambda;
import static cn.zenliu.java.mimic.test.LambdaMimic.internal.reflectWrapLambda;
import static org.jooq.lambda.tuple.Tuple.tuple;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public interface LambdaMimic {

    static @Nullable Tuple2<
        Boolean,//call remote if false ,should call locally (such as Supplier)
        Invokable> prepareLambda(Object arg) {
        if (arg == null || !isLambda(arg)) return null;
        final Tuple2<Boolean, Invokable> tuple = jvmWrapLambda(arg);
        return tuple == null ? reflectWrapLambda(arg) : tuple;
    }

    static boolean isLambda(Object arg) {
        return arg.getClass().toString().contains("$$Lambda");
    }

    final class internal {
        static Map<Class<?>, Tuple2<Boolean, Method>> cache = MimicApi.buildSoftConcurrentCache();

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
                    cache.put(arg.getClass(), tuple(true, declaredMethods[0]));
                } else {
                    cache.put(arg.getClass(), tuple(false, null));
                }
                return isLambda;
            }
        }

        static Tuple2<
            Boolean,//call remote if false ,should call locally (such as Supplier)
            Invokable> reflectWrapLambda(Object arg) {
            final boolean b = isReflectLambda(arg);
            if (!b) return null;
            final Tuple2<Boolean, Method> m = cache.get(arg.getClass());
            final Method method = m.v2;

            return tuple(method.getParameterCount() == 0, x -> {
                if (method.getParameterCount() > 0 && (x == null || x.length != method.getParameterCount()))
                    throw new IllegalArgumentException("argument length not match");
                try {
                    return method.invoke(arg, x);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @SuppressWarnings("unchecked")
        static Tuple2<
            Boolean,//call remote if false ,should call locally (such as Supplier)
            Invokable> wrapLambda(int length, Object invoke) {
            if (invoke instanceof Function) {
                final Function<Object[], Object> inv = (Function<Object[], Object>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    return inv.apply(args);
                });
            }
            if (invoke instanceof Predicate) {
                final Predicate<Object[]> inv = (Predicate<Object[]>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    return inv.test(args);
                });
            }
            if (invoke instanceof Supplier) {
                final Supplier<Object> inv = (Supplier<Object>) invoke;
                return tuple(false, args -> inv.get());
            }
            if (invoke instanceof Consumer) {
                final Consumer<Object[]> inv = (Consumer<Object[]>) invoke;
                return tuple(true, args -> {
                    if (length > 0 && args == null || args.length != length) {
                        throw new IllegalArgumentException("argument length not match !");
                    }
                    inv.accept(args);
                    return null;
                });
            }
            return null;
        }

        @SuppressWarnings("unchecked")
        static Tuple2<Boolean, Invokable> jvmWrapLambda(Object argument) {
            //@formatter:off
            if(argument instanceof BinaryOperator) return wrapLambda(2,wrapBinaryOperator((BinaryOperator<Object>) argument));
            if(argument instanceof BiConsumer) return wrapLambda(2,wrapBiConsumer((BiConsumer<Object, Object>) argument));
            if(argument instanceof BiFunction) return wrapLambda(2,wrapBiFunction((BiFunction<Object, Object, Object>) argument));
            if(argument instanceof BiPredicate) return wrapLambda(2,wrapBiPredicate((BiPredicate<Object, Object>) argument));
            if(argument instanceof BooleanSupplier) return wrapLambda(0,wrapBooleanSupplier((BooleanSupplier) argument));
            if(argument instanceof Consumer) return wrapLambda(1,wrapConsumer((Consumer<Object>) argument));
            if(argument instanceof DoubleBinaryOperator) return wrapLambda(2,wrapDoubleBinaryOperator((DoubleBinaryOperator) argument));
            if(argument instanceof DoubleConsumer) return wrapLambda(1,wrapDoubleConsumer((DoubleConsumer) argument));
            if(argument instanceof DoubleFunction) return wrapLambda(2,wrapDoubleFunction((DoubleFunction<Object>) argument));
            if(argument instanceof DoublePredicate) return wrapLambda(1,wrapDoublePredicate((DoublePredicate) argument));
            if(argument instanceof DoubleSupplier) return wrapLambda(0,wrapDoubleSupplier((DoubleSupplier) argument));
            if(argument instanceof DoubleToIntFunction) return wrapLambda(1,wrapDoubleToIntFunction((DoubleToIntFunction) argument));
            if(argument instanceof DoubleToLongFunction) return wrapLambda(1,wrapDoubleToLongFunction((DoubleToLongFunction) argument));
            if(argument instanceof DoubleUnaryOperator) return wrapLambda(1,wrapDoubleUnaryOperator((DoubleUnaryOperator) argument));
            if(argument instanceof UnaryOperator) return wrapLambda(1,wrapUnaryOperator((UnaryOperator<Object>) argument));
            if(argument instanceof Function) return wrapLambda(1,wrapFunction((Function<Object, Object>) argument));
            if(argument instanceof IntBinaryOperator) return wrapLambda(2,wrapIntBinaryOperator((IntBinaryOperator) argument));
            if(argument instanceof IntConsumer) return wrapLambda(1,wrapIntConsumer((IntConsumer) argument));
            if(argument instanceof IntFunction) return wrapLambda(1,wrapIntFunction((IntFunction<Object>) argument));
            if(argument instanceof IntPredicate) return wrapLambda(1,wrapIntPredicate((IntPredicate) argument));
            if(argument instanceof IntSupplier) return wrapLambda(0,wrapIntSupplier((IntSupplier) argument));
            if(argument instanceof IntToDoubleFunction) return wrapLambda(1,wrapIntToDoubleFunction((IntToDoubleFunction) argument));
            if(argument instanceof IntToLongFunction) return wrapLambda(1,wrapIntToLongFunction((IntToLongFunction) argument));
            if(argument instanceof IntUnaryOperator) return wrapLambda(1,wrapIntUnaryOperator((IntUnaryOperator) argument));
            if(argument instanceof LongBinaryOperator) return wrapLambda(2,wrapLongBinaryOperator((LongBinaryOperator) argument));
            if(argument instanceof LongConsumer) return wrapLambda(1,wrapLongConsumer((LongConsumer) argument));
            if(argument instanceof LongFunction) return wrapLambda(1,wrapLongFunction((LongFunction<Object>) argument));
            if(argument instanceof LongPredicate) return wrapLambda(1,wrapLongPredicate((LongPredicate) argument));
            if(argument instanceof LongSupplier) return wrapLambda(0,wrapLongSupplier((LongSupplier) argument));
            if(argument instanceof LongToDoubleFunction) return wrapLambda(1,wrapLongToDoubleFunction((LongToDoubleFunction) argument));
            if(argument instanceof LongToIntFunction) return wrapLambda(1,wrapLongToIntFunction((LongToIntFunction) argument));
            if(argument instanceof LongUnaryOperator) return wrapLambda(1,wrapLongUnaryOperator((LongUnaryOperator) argument));
            if(argument instanceof ObjDoubleConsumer) return wrapLambda(2,wrapObjDoubleConsumer((ObjDoubleConsumer<Object>) argument));
            if(argument instanceof ObjIntConsumer) return wrapLambda(2,wrapObjIntConsumer((ObjIntConsumer<Object>) argument));
            if(argument instanceof ObjLongConsumer) return wrapLambda(2,wrapObjLongConsumer((ObjLongConsumer<Object>) argument));
            if(argument instanceof Predicate) return wrapLambda(1,wrapPredicate((Predicate<Object>) argument));
            if(argument instanceof Supplier) return wrapLambda(0,wrapSupplier((Supplier<Object>) argument));
            if(argument instanceof ToDoubleBiFunction) return wrapLambda(2,wrapToDoubleBiFunction((ToDoubleBiFunction<Object, Object>) argument));
            if(argument instanceof ToDoubleFunction) return wrapLambda(1,wrapToDoubleFunction((ToDoubleFunction<Object>) argument));
            if(argument instanceof ToIntBiFunction) return wrapLambda(2,wrapToIntBiFunction((ToIntBiFunction<Object, Object>) argument));
            if(argument instanceof ToIntFunction) return wrapLambda(1,wrapToIntFunction((ToIntFunction<Object>) argument));
            if(argument instanceof ToLongBiFunction) return wrapLambda(2,wrapToLongBiFunction((ToLongBiFunction<Object, Object>) argument));
            if(argument instanceof ToLongFunction) return wrapLambda(1,wrapToLongFunction((ToLongFunction<Object>) argument));
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


    @FunctionalInterface
    interface Invokable {
        Object invoke(Object[] args);

        default Object invokeWith(Object... args) {
            return invoke(args);
        }

    }

    static void main(String[] args) {
        final BiConsumer<Object, Integer> a = (l, r) -> System.out.println(l + "" + r);
        final Function3<Long, Integer, String, String> b = (Function3<Long, Integer, String, String> & Serializable) (l, r, s) -> l + "" + r + s;
        final Integer ai = 1;
        System.out.println("isLambda(ai) = " + isLambda(ai));
        System.out.println("isLambda(ai) = " + isLambda(a));
        System.out.println("isLambda(ai) = " + isLambda(b));
        System.out.println("b.getClass() = " + b.getClass());
        System.out.println("b.getClass() = " + a.getClass());
        System.out.println("b.getClass().getDeclaredMethods() = " + Arrays.toString(b.getClass().getDeclaredMethods()));
    }
}
