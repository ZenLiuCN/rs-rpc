package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.api.Tick;
import cn.zenliu.java.rs.rpc.core.Rpc;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import lombok.Getter;
import lombok.val;
import mimic.MimicUtil;
import mimic.NULL;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Request Container
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-12
 */
public @Builder

class Request {
    /**
     * timestamp of sending
     */
    @Getter @Builder.Default final long tick = Tick.fromNowUTC();
    /**
     * arguments must not with Interfaces
     */
    final Object[] arguments;


    public Object[] getArguments(Function<Object, Object> argumentPreProcessor) {
        if (arguments == null || arguments.length == 0) return arguments;
        return Seq.of(arguments)
            .map(argumentPreProcessor)
            .map(x ->
                x instanceof NULL ? null :
                    Rpc.autoDelegate.get() ? MimicUtil.autoDisguise(x) : x
            ).toArray();
    }

    public static Payload build(String domain, String scope, Object[] arguments, BiFunction<Object, Long, Object> argumentPostProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        final Request request = Request.builder()
            .tick(tk)
            .arguments(proc(arguments, x -> argumentPostProcessor.apply(x, tk)))
            .build();
        val meta = Meta.builder().sign(domain).from(scope);
        if (trace) meta.trace(true);
        return DefaultPayload.create(Proto.to(request), Proto.to(meta.build()));
    }

    public static Request parseRequest(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.sliceData()), Request.class);
        } finally {
            p.release();
        }
    }

    public static Payload updateMeta(Payload p, Meta meta, @Nullable String name) {
        if (name != null) meta.addTrace(name);
        try {
            return DefaultPayload.create(
                p.sliceData().nioBuffer(),
                ByteBuffer.wrap(Proto.to(meta)));
        } finally {
            p.release();
        }
    }

    public static Payload buildCallback(String session, String scope, Object[] arguments, BiFunction<Object, Long, Object> argumentPostProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        final Request request = Request.builder()
            .tick(tk)
            .arguments(proc(arguments, x -> argumentPostProcessor.apply(x, tk)))
            .build();
        val meta = Meta.builder().sign(session).callback(true).from(scope);
        if (trace) meta.trace(true);
        return DefaultPayload.create(Proto.to(request), Proto.to(meta.build()));
    }

    @Override
    public String toString() {
        return "REQUEST@" + tick + '{' + Arrays.toString(arguments) + '}';
    }

    static Object[] proc(Object[] arguments, Function<Object, Object> argumentPostProcessor) {
        if (arguments == null || arguments.length == 0) return arguments;
        return Seq.of(arguments).map(x ->
            x == null ? NULL.Null :
                Rpc.autoDelegate.get() ? MimicUtil.autoMimic(x) : x
        ).map(argumentPostProcessor).toArray();
    }

//todo MimicLambda process

}

