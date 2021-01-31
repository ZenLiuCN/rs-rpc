package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.api.Tick;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.element.Meta;
import cn.zenliu.java.rs.rpc.core.element.Request;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.val;
import mimic.MimicUtil;
import mimic.NULL;
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
public final class RequestImpl implements Request {
    private final Payload payload;
    private volatile Meta meta;
    private volatile Argument argument;

    RequestImpl(Payload payload) {
        this.payload = payload;
    }

    @Override
    public boolean isInput() {
        return payload != null;
    }

    @Override
    public Request setMeta(Meta meta) {
        this.meta = meta;
        return this;
    }

    static Object[] proc(Object[] arguments, Function<Object, Object> postProcessor) {
        if (arguments == null || arguments.length == 0) return arguments;
        return Seq.of(arguments).map(x ->
            x == null ? NULL.Null :
                Rpc.autoDelegate.get() ? MimicUtil.autoMimic(x) : x
        ).map(postProcessor).toArray();
    }

    @Override
    public Request setArgument(Object[] arguments, Function<Object, Object> postProcessor) {
        this.argument = Argument.builder().arguments(proc(arguments, postProcessor)).build();
        return this;
    }

    @Override
    public Request setArgument(long tick, Object[] arguments, Function<Object, Object> postProcessor) {
        this.argument = Argument.builder().tick(tick).arguments(proc(arguments, postProcessor)).build();
        return this;
    }

    @Override
    public Payload build() {
        return DefaultPayload.create(Proto.to(argument), Proto.to(meta));
    }

    @Override
    public Meta getMeta() {
        if (meta == null) parseMeta();
        return meta;
    }


    private void parseMeta() {
        synchronized (this) {
            if (meta == null)
                meta = Proto.from(ByteBufUtil.getBytes(payload.sliceMetadata()), MetaImpl.class);
        }
    }

    private void parseArgument() {
        synchronized (this) {
            if (argument == null)
                try {
                    argument = Proto.from(ByteBufUtil.getBytes(payload.sliceData()), Argument.class);
                } finally {
                    payload.release();
                }
        }
    }

    @Override
    public long getTick() {
        if (argument == null) parseArgument();
        return argument.getTick();
    }

    @Override
    public Payload addTrace(String name) {
        if (name != null) return updateMeta(getMeta().addTrace(name));
        return payload;
    }

    @Override
    public Payload updateMeta(Meta meta) {
        try {
            return DefaultPayload.create(
                payload.sliceData().nioBuffer(),
                ByteBuffer.wrap(Proto.to(meta)));
        } finally {
            payload.release();
        }
    }

    public static Request of(Payload payload) {
        return new RequestImpl(payload);
    }

    @Override
    public Object[] getArguments(Function<Object, Object> argumentPreProcessor) {
        if (argument == null) parseArgument();
        if (argument.arguments == null || argument.arguments.length == 0) return argument.arguments;
        return Seq.of(argument.arguments)
            .map(argumentPreProcessor)
            .map(x ->
                x instanceof NULL ? null :
                    Rpc.autoDelegate.get() ? MimicUtil.autoDisguise(x) : x
            ).toArray();
    }

    @Override
    public String toString() {
        final StringBuilder s = new StringBuilder("REQUEST@[");
        if (meta != null) s.append(meta.toString());
        else if (payload != null) s.append(ByteBufUtil.prettyHexDump(payload.sliceMetadata()));
        s.append("]@");
        if (argument != null)
            s.append(argument.tick).append("{").append(Arrays.toString(argument.arguments)).append("}");
        else if (payload != null) s.append("{").append(ByteBufUtil.prettyHexDump(payload.sliceData())).append("}");
        return s.toString();

    }


    public static Payload build(String domain, String scope, Object[] arguments, BiFunction<Object, Long, Object> postProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        val meta = MetaImpl.builder().address(domain).from(scope);
        if (trace) meta.trace(true);
        return new RequestImpl(null).setMeta(meta.build()).setArgument(tk, arguments, x -> postProcessor.apply(x, tk)).build();
    }


    public static Payload buildCallback(String session, String scope, Object[] arguments, BiFunction<Object, Long, Object> postProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        val meta = MetaImpl.builder().address(session).callback(true).from(scope);
        if (trace) meta.trace(true);
        return new RequestImpl(null).setMeta(meta.build()).setArgument(tk, arguments, x -> postProcessor.apply(x, tk)).build();
    }


//todo MimicLambda process

}

