package cn.zenliu.java.rs.rpc.core.element;

import org.jetbrains.annotations.NotNull;

/**
 * A Server could be a RSocket Server or RSocket client
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface Server {
    boolean isClient();

    @NotNull String getName();

    String dump();

    Server setRemote(Remote remote);

    Remote getRemote();

    void removeRegistry();
}
