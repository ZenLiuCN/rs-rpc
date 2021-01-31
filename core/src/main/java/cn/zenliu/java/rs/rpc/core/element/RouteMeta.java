package cn.zenliu.java.rs.rpc.core.element;

import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Switch meta info between scope by a Remote
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface RouteMeta {
    /**
     * from scope name
     */
    @NotNull String getName();

    /**
     * does from scope use Resume (Server or Client)
     */
    boolean isResumeEnabled();

    /**
     * from scope routes
     */
    List<String> getRoutes();

    /**
     * from scope known routes of current scope
     */
    List<String> getKnown();

    boolean isKnown(Collection<String> routes);
}
