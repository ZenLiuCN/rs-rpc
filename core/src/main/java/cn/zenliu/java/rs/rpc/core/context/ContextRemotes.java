package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.UniqueList;

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

