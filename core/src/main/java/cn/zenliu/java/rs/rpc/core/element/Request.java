package cn.zenliu.java.rs.rpc.core.element;

import io.rsocket.Payload;

import java.util.function.Function;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface Request extends Transmit<Payload, Argument> {

    Object[] getArguments(Function<Object, Object> lambdaPreProcess);

    Request setArguments(Object[] arguments, Function<Object, Object> lambdaPostProcessor);

    Request setArguments(long tick, Object[] arguments, Function<Object, Object> lambdaPostProcessor);

}
