package cn.zenliu.java.mimic.api;

import mimic.*;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple4;

import java.util.Map;
import java.util.function.Predicate;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface MimicApi {
    /**
     * set interface predicate for need to mimic
     *
     * @param interfacePredicate the interface name predicate
     */
    static void setInterfacePredicate(@Nullable Predicate<String> interfacePredicate) {
        MimicUtil.setInterfacePredicate(interfacePredicate);
    }

    /**
     * use fluent getter mode to mimic a interface: which means not choose Getter Setter for just match JavaBean protocol
     */
    static void useFluentMode() {
        ReflectUtil.useFluentMode();

    }

    /**
     * use JavaBean protocol for Getter Setter match
     */
    static void useJavaBeanMode() {
        ReflectUtil.useJavaBeanMode();
    }

    /**
     * create a Light Delegator from a Instance
     *
     * @param instance the instance
     * @param type     the interface that instance is from
     * @param <T>      type
     */
    static <T> T proxy(T instance, Class<T> type) {
        return Proxy.of(type, instance);
    }

    /**
     * create a Light Delegator from a Instance. without Interface supplied, will found first one of {@link Class#getInterfaces()}
     *
     * @param instance the instance
     * @param <T>      type
     */
    static <T> T proxy(T instance) {
        return Proxy.of(instance);
    }

    /**
     * create a Empty Delegator from a interface type. better use this for it's contains setters
     *
     * @param type the interface type
     */
    static <T> T proxy(Class<T> type) {
        return Proxy.of(type, (Map<String, Object>) null);
    }

    /**
     * create a Empty Delegator from a interface type. return the Raw Proxy Object.
     *
     * @param type the interface type
     */
    static <T> Proxy<T> proxyOf(Class<T> type) {
        return Proxy.from(type);
    }

    /**
     * create a Light Delegator from a Instance. return the Raw Proxy Object.
     *
     * @param type the interface type
     */
    static <T> Proxy<T> proxyOf(T instance, Class<T> type) {
        return Proxy.from(type, instance);
    }

    /**
     * create a Nested Mimic of a Instance,return the Mimic Object
     *
     * @param instance the instance
     */
    @SuppressWarnings("unchecked")
    static <T> @Nullable Mimic<T> mimicOf(T instance) {
        final Object o = MimicUtil.autoMimic(instance);
        if (o instanceof Mimic) return (Mimic<T>) o;
        else return null;
    }

    /**
     * create a Nested Mimic of a Instance,return the proxy
     *
     * @param instance the instance
     */
    @SuppressWarnings("unchecked")
    static <T> T mimic(T instance) {
        return (T) MimicUtil.autoDisguise(MimicUtil.autoMimic(instance));
    }

    /**
     * convert a possible Delegator to real delegator object
     *
     * @param instance instance
     * @return not Delegator will return null
     */
    static @Nullable Delegator<?> reveal(Object instance) {
        final Object o = Delegator.tryRemoveProxy(instance);
        if (o instanceof Delegator) return (Delegator<?>) o;
        return null;
    }

    static <K, V> ConcurrentReferenceHashMap<K, V> buildSoftConcurrentCache() {
        return new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.STRONG, ConcurrentReferenceHashMap.ReferenceType.SOFT);
    }

    static <K, V> ConcurrentReferenceHashMap<K, V> buildWeakConcurrentCache() {
        return new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.STRONG, ConcurrentReferenceHashMap.ReferenceType.WEAK);
    }

    /**
     * call remote if true , else should call locally (such as Supplier)
     *
     * @param arg the argument to decide
     * @return null if not a lambda, (remote,invokable)
     */
    static @Nullable Tuple4<
        Boolean,//call remote if false ,should call locally (such as Supplier)
        Invokable,
        Class<?>,//the functional interface
        String //the method name
        > prepareLambda(Object arg) {
        return MimicLambdaUtil.prepareLambda(arg);
    }
}
