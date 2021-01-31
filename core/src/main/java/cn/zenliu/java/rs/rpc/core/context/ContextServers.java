package cn.zenliu.java.rs.rpc.core.context;

import reactor.core.Disposable;

import java.util.Map;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface ContextServers extends Context {

    Map<String, Disposable> getServers();

    default void addServer(Disposable server, String name) {
        getServers().put(name, server);
    }
}
