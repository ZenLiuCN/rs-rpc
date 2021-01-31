package cn.zenliu.java.rs.rpc.core.util;

import cn.zenliu.java.rs.rpc.core.element.Meta;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.Request;
import cn.zenliu.java.rs.rpc.core.element.RouteMeta;
import cn.zenliu.java.rs.rpc.core.proto.*;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.ByteBuffer;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-22
 */
public interface PayloadUtil {

    static MetaImpl maybeMeta(Payload p) {
        if (p.hasMetadata()) {
            return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), MetaImpl.class);
        }
        return null;
    }

    static Payload buildRouteMeta(RouteMeta meta) {
        return DefaultPayload.create(DefaultPayload.EMPTY_BUFFER, ByteBuffer.wrap(Proto.to(meta)));
    }

    static RouteMeta maybeServMeta(Payload p) {
        if (p.hasMetadata()) {
            try {
                return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), RouteMetaImpl.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    static Tuple2<Meta, Payload> justMeta(Payload p) {
        return Tuple.tuple(Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), MetaImpl.class), p);
    }

    static RequestImpl mustRequest(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.data()), RequestImpl.class);
        } finally {
            p.release();
        }
    }

    static Response mustResponse(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.data()), Response.class);
        } finally {
            p.release();
        }
    }

    static Mono<Void> metaPushHandler(Payload p, Remote r, Consumer<Tuple2<RouteMeta, Remote>> servMetaHandler) {
        return Mono.just(p)
            .map(PayloadUtil::maybeServMeta)//todo
            .flatMap(x -> {
                servMetaHandler.accept(Tuple.tuple(x, r));
                return Mono.empty();
            });
    }

    static Mono<Void> fnfHandler(Payload p, Remote r, Consumer<Tuple2<@NotNull RouteMeta, @NotNull Remote>> servMetaHandler, BiFunction<Request, Remote, Mono<Void>> fnfHandler) {
        if (p.data().capacity() == 0) { // a meta push must without data
            final RouteMeta routeMeta = maybeServMeta(p);
            if (routeMeta != null) {
                servMetaHandler.accept(Tuple.tuple(routeMeta, r));
                return Mono.empty();
            }
        }
        return Mono.just(p)
            .map(RequestImpl::of)
            .flatMap(x -> fnfHandler.apply(x, r));
    }

    static Mono<Payload> rrHandler(Payload p, Remote r, BiFunction<Request, Remote, Mono<Payload>> rrHandler) {
        return Mono.just(p)
            .map(RequestImpl::of)
            .flatMap(x -> rrHandler.apply(x, r));
    }

    static Flux<Payload> rsHandler(Payload p, Remote r, BiFunction<Request, Remote, Flux<Payload>> rsHandler) {
        return rsHandler.apply(RequestImpl.of(p), r);
    }
}
