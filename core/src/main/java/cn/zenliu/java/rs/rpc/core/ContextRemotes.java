package cn.zenliu.java.rs.rpc.core;

import java.util.Map;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface ContextRemotes extends Context {
    Map<Integer, Remote> getRemotes();

    UniqueList getRemoteNames();

    default int prepareRemoteName(String name) {
        return getRemoteNames().prepare(name);
    }

}

