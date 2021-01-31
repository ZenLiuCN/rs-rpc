package cn.zenliu.java.rs.rpc.core.element;

import io.rsocket.Payload;

import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface Request {
    Meta getMeta();

    long getTick();

    Payload updateMeta(Meta meta);

    Payload addTrace(String name);

    Object[] getArguments(Function<Object, Object> preProcessor);

    boolean isInput();

    Request setMeta(Meta meta);

    Request setArgument(Object[] arguments, Function<Object, Object> postProcessor);

    Request setArgument(long tick, Object[] arguments, Function<Object, Object> postProcessor);

    Payload build();
}
