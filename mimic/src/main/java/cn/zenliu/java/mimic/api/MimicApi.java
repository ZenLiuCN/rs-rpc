package cn.zenliu.java.mimic.api;

import mimic.*;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.tuple.Tuple4;

import java.time.Duration;
import java.util.Map;
import java.util.function.Predicate;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface MimicApi {
    /**
     * auto purify caches, execute {@link MimicApi#purifyCaches()} in a new daemon thread
     */
    static void autoPurifyCaches() {
        final Thread executor = new Thread(() -> {
            while (true) {
                purifyCaches();
            }
        }, "cache-purifier");
        executor.setDaemon(true);
        executor.start();
    }

    /**
     * purify caches to remove empty references
     */
    static void purifyCaches() {
        Cache.purifyAll();
    }

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
        return MimicLight.of(type, instance);
    }

    /**
     * create a Light Delegator from a Instance. without Interface supplied, will found first one of {@link Class#getInterfaces()}
     *
     * @param instance the instance
     * @param <T>      type
     */
    static <T> T proxy(T instance) {
        return MimicLight.of(instance);
    }

    /**
     * create a Empty Delegator from a interface type. better use this for it's contains setters
     *
     * @param type the interface type
     */
    static <T> T proxy(Class<T> type) {
        return MimicLight.of(type, (Map<String, Object>) null);
    }

    /**
     * create a Empty Delegator from a interface type. return the Raw Proxy Object.
     *
     * @param type the interface type
     */
    static <T> MimicLight<T> proxyOf(Class<T> type) {
        return MimicLight.from(type);
    }

    /**
     * create a Light Delegator from a Instance. return the Raw Proxy Object.
     *
     * @param type the interface type
     */
    static <T> MimicLight<T> proxyOf(T instance, Class<T> type) {
        return MimicLight.from(type, instance);
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
    static @Nullable Mimic<?> reveal(Object instance) {
        final Object o = Mimic.tryRemoveProxy(instance);
        if (o instanceof Mimic) return (Mimic<?>) o;
        return null;
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

    static <K, V> Cache<K, V> buildCache(@Nullable Duration ttl, boolean softOrWeak) {
        return Cache.build(ttl, softOrWeak);
    }
}
