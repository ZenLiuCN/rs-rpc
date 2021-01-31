package cn.zenliu.java.rs.rpc.core.element;

import org.jetbrains.annotations.Nullable;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface RouteTable {

    @Nullable Remote findRoute(String address);

    RouteTable addRoute(RouteMeta meta);

}
