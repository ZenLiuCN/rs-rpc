package cn.zenliu.java.rs.rpc.rpc;

import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.impl.ScopeImpl;
import org.slf4j.Logger;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface Util {
    static void registerShutdown(Scope service, Logger log) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            final ScopeImpl scope = (ScopeImpl) service;
            log.warn("service {} info {} handler {}", service.getName(), scope.getRoutes().get(), scope.getSigns());
            service.release();
        }));
    }
}
