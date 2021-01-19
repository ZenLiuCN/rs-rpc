package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Delegator;
import cn.zenliu.java.rs.rpc.api.JvmUnique;
import cn.zenliu.java.rs.rpc.api.Scope;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
     * Create Delegator from Instance with Class
     *
     * @return Proxy with data from Instance
     */
    static <T> T delegate(T instance, Class<T> noneNestedInterface) {
        return Delegator.proxy(noneNestedInterface, instance);
    }

    /**
     * Create Delegator from Map Data with Class
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
     * Create Delegator of Class
     *
     * @param noneNestedInterfaceObject none nested Interface
     * @return Proxy without data
     */
    static <T> T delegate(T noneNestedInterfaceObject) {
        return Delegator.proxy(noneNestedInterfaceObject);
    }
}
