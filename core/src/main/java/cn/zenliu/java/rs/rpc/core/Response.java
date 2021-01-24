package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.api.Tick;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/**
 * RPC Response
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-12
 */
public @Builder(access = AccessLevel.PACKAGE)
final class Response {
    /**
     * tick of sending
     */
    @Getter @Builder.Default final long tick = Tick.fromNowUTC();
    /**
     * Result
     */
    final Result<Object> response;

    public Result<Object> getResponse() {
        return Rpc.autoDelegate.get() ? response.map(Mimic::autoDelegate) : response;
    }

    public static Payload build(Meta meta, String name, Result<Object> result) {
        return DefaultPayload.create(Proto.to(Response.builder().response(
            Rpc.autoDelegate.get() ? result.map(Mimic::autoBuild) : result
        ).build()), Proto.to(name != null ? meta.addTrace(name) : meta));
    }

    public static Meta parseMeta(Payload p) {
        return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), Meta.class);
    }

    public static Response parse(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.sliceData()), Response.class);
        } finally {
            p.release();
        }
    }
    @Override
    public String toString() {
        return "RESPOND@" + tick + '{' + response + '}';
    }

}