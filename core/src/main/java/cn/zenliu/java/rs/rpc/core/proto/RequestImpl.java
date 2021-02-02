package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.api.Tick;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.element.Argument;
import cn.zenliu.java.rs.rpc.core.element.Meta;
import cn.zenliu.java.rs.rpc.core.element.Request;
import cn.zenliu.java.rs.rpc.core.element.Transmit;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import lombok.val;
import mimic.MimicUtil;
import mimic.NULL;
import org.jooq.lambda.Seq;

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
public final class RequestImpl extends Transmit.BaseTransmit<Argument> implements Request {
    static final Class<MetaImpl> TYPE_META = MetaImpl.class;
    static final Class<Argument> TYPE_DATA = Argument.class;

    RequestImpl(Payload payload) {
        super(payload);
    }

    static Object[] proc(Object[] arguments, Function<Object, Object> postProcessor) {
        if (arguments == null || arguments.length == 0) return arguments;
        return Seq.of(arguments).map(x ->
            x == null ? NULL.Null :
                Rpc.autoDelegate.get() ? MimicUtil.autoMimic(x) : x
        ).map(postProcessor).toArray();
    }

    @Override
    protected Class<? extends Meta> metaType() {
        return TYPE_META;
    }

    @Override
    protected Class<? extends Argument> dataType() {
        return TYPE_DATA;
    }

    @Override
    public Request setArguments(Object[] arguments, Function<Object, Object> postProcessor) {
        this.data = Argument.builder().arguments(proc(arguments, postProcessor)).build();
        return this;
    }

    @Override
    public Request setArguments(long tick, Object[] arguments, Function<Object, Object> postProcessor) {
        this.data = Argument.builder().tick(tick).arguments(proc(arguments, postProcessor)).build();
        return this;
    }

    @Override
    public Request addTrace(String name) {
        super.innerAddTrace(name);
        return this;
    }

    @Override
    public Request updateMeta(Meta meta) {
        super.innerUpdateMeta(meta);
        return this;
    }

    @Override
    public Request setMeta(Meta meta) {
        super.innerSetMeta(meta);
        return this;
    }

    @Override
    public long getTick() {
        if (data == null) parseData();
        return data.getTick();
    }

    @Override
    public Object[] getArguments(Function<Object, Object> lambdaPreProcess) {
        if (data == null) parseData();
        if (data.isEmpty()) return data.getArguments();
        return Seq.of(data.getArguments())
            .map(lambdaPreProcess)
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
        if (data != null) {
            s.append(data.getTick()).append("{").append(Arrays.toString(data.getArguments())).append("}");
        } else if (payload != null) s.append("{").append(ByteBufUtil.prettyHexDump(payload.sliceData())).append("}");
        return s.toString();

    }


    public static Request of(Payload payload) {
        return new RequestImpl(payload);
    }


    public static Payload build(String domain, String scope, Object[] arguments, BiFunction<Object, Long, Object> lambdaPostProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        val meta = MetaImpl.builder().address(domain).from(scope);
        if (trace) meta.trace(true);
        return new RequestImpl(null).setMeta(meta.build()).setArguments(tk, arguments, x -> lambdaPostProcessor.apply(x, tk)).build();
    }


    public static Payload buildCallback(String session, String scope, Object[] arguments, BiFunction<Object, Long, Object> lambdaPostProcessor, boolean trace) {
        final long tk = Tick.fromNowUTC();
        val meta = MetaImpl.builder().address(session).callback(true).from(scope);
        if (trace) meta.trace(true);
        return new RequestImpl(null).setMeta(meta.build()).setArguments(tk, arguments, x -> lambdaPostProcessor.apply(x, tk)).build();
    }


//todo MimicLambda process

}

