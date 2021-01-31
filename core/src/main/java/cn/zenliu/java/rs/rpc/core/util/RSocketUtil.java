package cn.zenliu.java.rs.rpc.core.util;

import cn.zenliu.java.rs.rpc.api.Config;
import io.netty.buffer.Unpooled;
import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.core.Resume;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
public interface RSocketUtil {


    static ServerTransport<? extends Closeable> buildTransport(Config.ServerConfig config) {
        switch (config.getMode()) {
            case 0:
                return config.getBindAddress() != null ?
                    TcpServerTransport.create(config.getBindAddress(),
                        Objects.requireNonNull(config.getPort(), "must with PORT defined for server mode 0"))
                    : TcpServerTransport.create(Objects.requireNonNull(config.getPort(), "must with PORT defined for server mode 0"));
            //TODO how to isolate HTTPServer?


        }
        throw new IllegalStateException("not supported transport mode:" + config.getMode());
    }

    static Resume buildResume(Config.ResumeSetting config, boolean client) {
        final Resume resume = new Resume();
        if (client) if (config.getRetry() != null) resume.retry(buildRetry(config.getRetry()));
        if (config.isCleanupStoreOnKeepAlive()) resume.cleanupStoreOnKeepAlive();

        if (client && config.getToken() != null) {
            final byte[] token = config.getToken().getBytes(StandardCharsets.UTF_8);
            resume.token(() -> Unpooled.wrappedBuffer(token));
        }
        if (config.getSessionDuration() != null) resume.sessionDuration(config.getSessionDuration());
        if (config.getStreamTimeout() != null) resume.streamTimeout(config.getStreamTimeout());
        return resume;

    }

    static ClientTransport buildTransport(Config.ClientConfig config) {
        switch (config.getMode()) {
            case 0:
                return TcpClientTransport.create(
                    Objects.requireNonNull(config.getHost(), "must with HOST defined for client mode 0"),
                    Objects.requireNonNull(config.getPort(), "must with PORT defined for client mode 0"));
            case 1:
                return WebsocketClientTransport.create(
                    Objects.requireNonNull(config.getUri(), "must with URI defined for client mode 1"));
        }
        throw new IllegalStateException("not supported transport mode:" + config.getMode());
    }

    static Retry buildRetry(Config.Retry retry) {
        if (retry instanceof Config.Retry.FixedDelay) {
            return Retry.fixedDelay(((Config.Retry.FixedDelay) retry).getMaxAttempts(), ((Config.Retry.FixedDelay) retry).getFixedDelay());
        } else if (retry instanceof Config.Retry.Backoff) {
            return Retry.backoff(((Config.Retry.Backoff) retry).getMaxAttempts(), ((Config.Retry.Backoff) retry).getMinDelay());
        } else if (retry instanceof Config.Retry.Max) {
            return Retry.max(((Config.Retry.Max) retry).getMaxAttempts());
        }
        return Retry.indefinitely();
    }

    static Supplier<Mono<? extends Closeable>> buildServer(SocketAcceptor acceptor, Config.ServerConfig config) {
        return () -> {
            final ServerTransport<? extends Closeable> transport = buildTransport(config);
            final RSocketServer builder = RSocketServer.create()
                .payloadDecoder(config.getPayloadCodec() == 1 ? PayloadDecoder.DEFAULT : PayloadDecoder.ZERO_COPY)
                .acceptor(acceptor);
            if (config.getResume() != null) builder.resume(buildResume(config.getResume(), false));
            if (config.getFragment() != null) builder.fragment(config.getFragment());
            if (config.getMaxInboundPayloadSize() != null)
                builder.maxInboundPayloadSize(config.getMaxInboundPayloadSize());
            return builder.bind(transport);
        };
    }

    @FunctionalInterface
    interface SetupSupplierBuilder {
        Mono<Payload> build(boolean resume);
    }

    static Supplier<Mono<? extends Closeable>> buildClient(SocketAcceptor acceptor, SetupSupplierBuilder setupSupplierBuilder, Config.ClientConfig config) {
        return () -> {
            final RSocketConnector builder = RSocketConnector.create()
                .reconnect(buildRetry(config.getRetry()))
                .payloadDecoder(config.getPayloadCodec() == 1 ? PayloadDecoder.DEFAULT : PayloadDecoder.ZERO_COPY)
                .keepAlive(
                    config.getKeepAliveInterval() == null ? Duration.ofSeconds(20) : config.getKeepAliveInterval(),
                    config.getKeepAliveMaxLifeTime() == null ? Duration.ofSeconds(90) : config.getKeepAliveMaxLifeTime()
                )
                .setupPayload(setupSupplierBuilder.build(config.getResume() != null)) //current setup from client is support resume or not
                .acceptor(acceptor);
            if (config.getFragment() != null) builder.fragment(config.getFragment());
            if (config.getMaxInboundPayloadSize() != null)
                builder.maxInboundPayloadSize(config.getMaxInboundPayloadSize());
            if (config.getResume() != null) builder
                .resume(buildResume(config.getResume(), true));
            return builder.connect(buildTransport(config));
        };

    }
}
