package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.core.proto.RouteMetaImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
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

    static RouteMeta from(String scopeName, Collection<String> routes, @Nullable Collection<String> known) {
        final RouteMetaImpl.RouteMetaImplBuilder builder = RouteMetaImpl.builder()
            .name(scopeName)
            .routes(new ArrayList<>(routes));
        if (known != null) builder.known(new ArrayList<>(known));
        return builder.build();
    }
}
