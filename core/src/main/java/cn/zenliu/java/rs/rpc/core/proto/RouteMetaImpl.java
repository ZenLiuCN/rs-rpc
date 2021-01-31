package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.core.element.RouteMeta;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-18
 */
@Builder
@ToString
public class RouteMetaImpl implements RouteMeta {
    private static final long serialVersionUID = -3906227121661168621L;

    @Getter final String name;
    @Getter final boolean resumeEnabled;

    @Getter @Builder.Default final @Nullable List<String> routes = new ArrayList<>();
    @Getter @Builder.Default final @Nullable List<String> known = new ArrayList<>();

    public boolean isKnown(Collection<String> routes) {
        return (routes.isEmpty() && (known == null || known.isEmpty()))
            || (known != null) && known.containsAll(routes);
    }


}
