package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.api.Result;

import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-20
 */
@FunctionalInterface
public interface ServiceMethod extends Function<Object[], Result<Object>> {
}
