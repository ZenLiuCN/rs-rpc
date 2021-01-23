package cn.zenliu.java.rs.rpc.core;

import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import lombok.Getter;
import lombok.val;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * Request Container
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-12
 */
public @Builder
@Getter
class Request {
    /**
     * timestamp of sending
     */
    @Builder.Default final long timestamp = System.currentTimeMillis();
    /**
     * arguments must not with Interfaces
     */
    final Object[] arguments;

    public static Payload build(String domain, String scope, Object[] arguments, boolean trace) {
        final Request request = Request.builder()
            .arguments(arguments)
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

    public static Meta parseMeta(Payload p) {
        return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), Meta.class);
    }

    public static Payload updateMeta(Payload p, Meta meta, @Nullable String name) {
        if (name != null) meta.addTrace(name);
        return DefaultPayload.create(
            p.sliceData().nioBuffer(),
            ByteBuffer.wrap(Proto.to(meta)));
    }

    @Override
    public String toString() {
        return "REQUEST@" + timestamp + '{' + Arrays.toString(arguments) + '}';
    }

}

