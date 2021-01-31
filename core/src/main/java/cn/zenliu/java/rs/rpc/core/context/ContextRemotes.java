package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.UniqueList;
import org.jetbrains.annotations.Nullable;

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

    default @Nullable Remote findRemoteByName(String name) {
        final int i = getRemoteNames().indexOf(name);
        if (i < 0) return null;
        return getRemotes().get(i);
    }

}

