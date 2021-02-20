package cn.zenliu.java.rs.rpc.core.element;

import reactor.core.publisher.Flux;

import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-20
 */
@FunctionalInterface
public interface ServiceStreamMethod extends Function<Object[], Flux<Object>> {
}
