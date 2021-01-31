package cn.zenliu.java.rs.rpc.core;

import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import org.jetbrains.annotations.NotNull;
import org.jooq.lambda.tuple.Tuple;
import org.jooq.lambda.tuple.Tuple2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-22
 */
public interface FunctorPayload {

    static Meta maybeMeta(Payload p) {
        if (p.hasMetadata()) {
            return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), Meta.class);
        }
        return null;
    }

    static ServMeta maybeServMeta(Payload p) {
        if (p.hasMetadata()) {
            try {
                return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), ServMeta.class);
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    static Tuple2<Meta, Payload> justMeta(Payload p) {
        return Tuple.tuple(Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), Meta.class), p);
    }

    static Request mustRequest(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.data()), Request.class);
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

    static Mono<Void> metaPushHandler(Payload p, Remote r, Consumer<Tuple2<ServMeta, Remote>> servMetaHandler) {
        return Mono.just(p)
            .map(FunctorPayload::maybeServMeta)//todo
            .flatMap(x -> {
                servMetaHandler.accept(Tuple.tuple(x, r));
                return Mono.empty();
            });
    }

    static Mono<Void> fnfHandler(Payload p, Remote r, Consumer<Tuple2<@NotNull ServMeta, @NotNull Remote>> servMetaHandler, BiFunction<Tuple2<Meta, Payload>, Remote, Mono<Void>> fnfHandler) {
        if (p.data().capacity() == 0) { // a meta push must without data
            final ServMeta servMeta = maybeServMeta(p);
            if (servMeta != null) {
                servMetaHandler.accept(Tuple.tuple(servMeta, r));
                return Mono.empty();
            }
        }
        return Mono.just(p)
            .map(FunctorPayload::justMeta)
            .flatMap(x -> fnfHandler.apply(x, r));
    }

    static Mono<Payload> rrHandler(Payload p, Remote r, BiFunction<Tuple2<Meta, Payload>, Remote, Mono<Payload>> rrHandler) {
        return Mono.just(p)
            .map(FunctorPayload::justMeta)
            .flatMap(x -> rrHandler.apply(x, r));
    }

    static Flux<Payload> rsHandler(Payload p, Remote r, BiFunction<Tuple2<Meta, Payload>, Remote, Flux<Payload>> rsHandler) {
        return rsHandler.apply(justMeta(p), r);
    }
}
