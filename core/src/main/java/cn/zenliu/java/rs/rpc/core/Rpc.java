package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.JvmUnique;
import cn.zenliu.java.rs.rpc.api.Scope;
import mimic.MimicUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-15
 */
public interface Rpc {
    String GLOBAL_NAME = "Global";
    /**
     * Global Scope (never routeing)
     */
    Scope Global = ScopeImpl.builder().name(JvmUnique.uniqueNameWithRandom(GLOBAL_NAME)).route(true).build();
    Map<String, Scope> scopes = new ConcurrentHashMap<>(2);
    boolean withGlobal = internal.nothing();
    AtomicBoolean autoDelegate = new AtomicBoolean(false);

    /**
     * use auto delegate for remote all(default false)
     *
     * @param use does use
     */
    static void setAutoDelegate(boolean use) {
        autoDelegate.set(use);
    }

    /**
     * setting auto delegate interfaces predication
     *
     * @param predicate the method
     */
    static void setMimicInterfaces(Predicate<String> predicate) {
        MimicUtil.interfaceNamePredicate.set(predicate == null ? x -> true : predicate);
    }

    /**
     * create a new Scope
     *
     * @param name     scope name
     * @param routeing does using routeing
     * @return new Scope
     */
    static Scope newScope(@NotNull String name, boolean routeing) {
        String trueName = JvmUnique.uniqueNameWithoutRandom(name);
        if (scopes.containsKey(trueName))
            throw new IllegalArgumentException("name of scope is already exists! " + trueName);
        final ScopeImpl newScope = ScopeImpl.builder().name(trueName).route(routeing).build();
        scopes.put(trueName, newScope);
        return newScope;
    }

    /**
     * fetch a exists scope or create new one
     *
     * @param name     scope name
     * @param routeing does using routeing
     * @return already exists one not change routeing status!
     */
    static Scope fetchOrCreate(@NotNull String name, boolean routeing) {
        if (name.equals(GLOBAL_NAME)) return Global;
        String trueName = JvmUnique.uniqueNameWithoutRandom(name);
        if (scopes.containsKey(trueName))
            return scopes.get(trueName);
        final ScopeImpl newScope = ScopeImpl.builder().name(trueName).route(routeing).build();
        scopes.put(trueName, newScope);
        return newScope;
    }

    final class internal {
        static boolean nothing() {
            scopes.put(Global.getName(), Global);
            return true;
        }
    }

    /**
     * Create Light Delegator from Instance with Class
     *
     * @return Proxy with data from Instance
     */
    static <T> T delegate(T instance, Class<T> noneNestedInterface) {
        return Delegator.proxy(noneNestedInterface, instance);
    }

    /**
     * Create Light Delegator from Map Data with Class
     *
     * @param values              data ( key is PascalCase)
     * @param noneNestedInterface none nested Interface
     * @param <T>                 the type
     * @return proxy with data from values
     */
    static <T> T delegate(Class<T> noneNestedInterface, Map<String, Object> values) {
        return Delegator.proxy(noneNestedInterface, values);
    }

    /**
     * Create Light Delegator of Class
     *
     * @param noneNestedInterfaceObject none nested Interface
     * @return Proxy with data from instance
     */
    static <T> T delegate(T noneNestedInterfaceObject) {
        return Delegator.proxy(noneNestedInterfaceObject);
    }

    /**
     * Create Deep Delegator of Class
     *
     * @param interfaceInstance instance
     * @return Proxy without data
     */
    @SuppressWarnings("unchecked")
    static <T> T delegateDeep(T interfaceInstance) {
        return (T) MimicUtil.autoMimic(interfaceInstance);
    }

    /**
     * Create Light Delegator from Instance with Class
     *
     * @param instance      the instance
     * @param interfaceType the interface type of instance
     * @return Proxy with data from Instance
     */
    static <T> T delegateDeep(T instance, Class<T> interfaceType) {
        return (T) MimicUtil.mimic(instance, interfaceType).delegate();
    }
}
