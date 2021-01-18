package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Result;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import lombok.Getter;

/**
 * RPC Response
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-12
 */
public @Builder
@Getter
final class Response {
    /**
     * timestamp of sending
     */
    @Builder.Default final long timestamp = System.currentTimeMillis();
    /**
     * Result
     */
    final Result<Object> response;

    public static Payload build(Meta meta, String name, Result<Object> result) {
        return DefaultPayload.create(Proto.to(Response.builder().response(result).build()), Proto.to(meta.addTrace(name)));
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
        return "\n--------------RESPONSE----------------" +
            "\n timestamp=" + timestamp +
            "\n response=" + response +
            "\n----------------------------------------";
    }
}