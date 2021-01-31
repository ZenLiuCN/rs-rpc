package cn.zenliu.java.rs.rpc.core.element;

import org.jetbrains.annotations.NotNull;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface Meta {
    Meta addTrace(@NotNull String scope);

    String cost();

    String costNow();

    String getAddress();

    String getFrom();

    boolean isCallback();

    long getTick();

    boolean isTrace();

    String getUuid();

    java.util.Map<Long, String> getLink();
}
