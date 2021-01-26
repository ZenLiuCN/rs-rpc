package cn.zenliu.java.mimic.api;

import mimic.*;
import org.jetbrains.annotations.Nullable;

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
    static void useFluentGetterMode() {
        ReflectUtil.setGetterPredicate(ReflectUtil::fluentBeanGetterPredicate);
    }

    /**
     * use JavaBean protocol for Getter Setter match
     */
    static void useJavaBeanGetterMode() {
        ReflectUtil.setGetterPredicate(ReflectUtil::javaBeanGetterPredicate);
    }

    static <T> T proxy(T instance, Class<T> type) {
        return Proxy.of(type, instance);
    }

    static <T> T proxy(T instance) {
        return Proxy.of(instance);
    }

    static <T> T proxy(Class<T> type) {
        return Proxy.of(type, (Map<String, Object>) null);
    }

    static <T> Proxy<T> proxyOf(Class<T> type) {
        return Proxy.from(type);
    }

    static <T> Proxy<T> proxyOf(T instance, Class<T> type) {
        return Proxy.from(type, instance);
    }

    static <T> @Nullable Mimic<T> mimicOf(T instance) {
        final Object o = MimicUtil.autoMimic(instance);
        if (o instanceof Mimic) return (Mimic<T>) o;
        else return null;
    }

    static <T> T mimic(T instance) {
        return (T) MimicUtil.autoDisguise(MimicUtil.autoMimic(instance));
    }

    static @Nullable Delegator<?> reveal(Object instance) {
        final Object o = Delegator.tryRemoveProxy(instance);
        if (o instanceof Delegator) return (Delegator<?>) o;
        return null;
    }
}
