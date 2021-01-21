package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.ProxyUtil.ClientCreator;
import cn.zenliu.java.rs.rpc.core.ProxyUtil.ServiceRegister;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.core.RSocketConnector;
import io.rsocket.core.RSocketServer;
import io.rsocket.core.Resume;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
import io.rsocket.transport.netty.client.TcpClientTransport;
import io.rsocket.transport.netty.client.WebsocketClientTransport;
import io.rsocket.transport.netty.server.TcpServerTransport;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static cn.zenliu.java.rs.rpc.core.ProxyUtil.clientCreatorBuilder;
import static cn.zenliu.java.rs.rpc.core.ProxyUtil.serviceRegisterBuilder;


/**
 * Scope implement
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */


public final class ScopeImpl extends ScopeContextImpl implements Scope, RouteingContext, Serializable {
    private static final long serialVersionUID = -7734615300814212060L;


    static String LOG_META = "\n META: {}\n SERVICE: {}";
    static String LOG_META_REQUEST = "\n META:{}\n REQUEST: {}\n SERVICE: {}";

    transient final ClientCreator clientCreator;
    transient final ServiceRegister serviceRegister = serviceRegisterBuilder(this::addService, this::addHandler);


    @Builder
    public ScopeImpl(String name, boolean route) {
        super(name == null || name.isEmpty() ? UUID.randomUUID().toString() : name, route);
        clientCreator = clientCreatorBuilder(name, this::routeingFNF, this::routeingRR);
        calcRoutes();
    }

    static String dump(Remote remote, Payload payload) {
        return "\n SERVICE: " + remote.name + " | SERVICES:" + remote.service + "| WEIGHT:" + remote.weight + "\n" +
            " META :\n" + ByteBufUtil.prettyHexDump(payload.sliceMetadata()) +
            "\n DATA :\n" + ByteBufUtil.prettyHexDump(payload.sliceData());
    }


    //region RSocket Handlers


    /**
     * Process FireAndForgot
     *
     * @param p      payload
     * @param remote meta of remote
     */
    void handleFNF(Payload p, Remote remote) {
        log.debug("[{}] received  FNF from {}", name, remote.name);
        if (p.data().capacity() == 0) { // a meta push must without data
            log.debug("[{}]  maybe received ServMeta via FNF from {}", name, remote.name);
            ServMeta result = remote.tryHandleMeta(p);
            if (result != null) {
                servMetaProcess(result, remote);
                return;
            }
        }
        final Meta meta = Request.parseMeta(p);
        onDebug(log -> log.debug("[{}] begin to process FireAndForget:" + LOG_META, name, meta, remote));
        final Function<Object[], Result<Object>> handler = findHandler(meta.sign);
        if (handler != null) {
            final Request request = Request.parseRequest(p);
            onDebugWithTimer(
                log -> log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST, name, meta, request, remote)
                , null
                , log -> {
                    try {
                        handler.apply(request.arguments);
                    } catch (Exception e) {
                        log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, name, meta, request, remote, e);
                    }
                });
        } else if (route) {
            final String domain = meta.sign.substring(0, ProxyUtil.DOMAIN_SPLITTER);
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(log -> log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, remote, service)
                    , log -> log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, remote.name, service.name));
                service.socket.fireAndForget(Request.updateMeta(p, meta, (debug.get() || trace.get() || meta.track) ? name : null));
                return;
            }
        }
        log.error("[{}] none registered FireAndForget:" + LOG_META, name, meta, remote);
    }


    /**
     * RequestResponse
     *
     * @param p      payload
     * @param remote remote
     * @return response Payload
     */
    Mono<Payload> handleRR(Payload p, Remote remote) {
        final Meta meta = Request.parseMeta(p);
        onDebug(log -> log.debug("[{}] process RequestAndResponse:" + LOG_META, name, meta, remote));
        final Function<Object[], Result<Object>> handler = findHandler(meta.sign);
        if (handler != null) {
            final Request request = Request.parseRequest(p);
            final Payload result = onDebugWithTimerReturns(
                log -> log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST, name, meta, request, remote)
                , null
                , log -> {
                    try {
                        final Result<Object> res = handler.apply(request.arguments);
                        return Response.build(meta, (debug.get() || trace.get() || meta.track) ? name : null, res != null ? res : Result.ok(null));
                    } catch (Exception ex) {
                        log.error("[{}] error on process RequestAndResponse:" + LOG_META_REQUEST, name, meta, request, remote, ex);
                        return Response.build(meta, name, Result.error(ex));
                    }
                }
            );
            return Mono.just(result);
        } else if (route) {
            final String domain = meta.sign.substring(0, meta.sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
            final Remote service = findRemoteService(domain);
            if (service != null) {
                onDebugElse(log -> log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, remote, service)
                    , log -> log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, remote.name, service.name));
                try {
                    return service.socket.requestResponse(Request.updateMeta(p, meta, (debug.get() || trace.get() || meta.track) ? name : null));
                } catch (Exception ex) {
                    log.error("[{}] error on process routeing RequestAndResponse:" + LOG_META, name, meta, remote, ex);
                    return Mono.just(Response.build(meta, name, Result.error(ex)));
                }
            }
        }
        log.error("[{}] none registered RequestAndResponse:" + LOG_META, name, meta, remote);
        return Mono.just(Response.build(meta, name, Result.error(new IllegalStateException("no such method '" + meta.sign + "' on " +
            name + ",I supports " + routes.get() + " with routeing " + route + (route ? (" and with routes " + remoteDomains) : "")))));
    }

    //endregion
    //region Service Call Routeing
    void routeingFNF(String sign, Object[] args) {
        final String domain = sign.substring(0, sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Remote remote = findRemoteService(domain);
        if (null == remote) {
            throw new IllegalStateException("not exists service for " + sign);
        }
        remote.socket.fireAndForget(Request.build(sign, name, args, debug.get() || trace.get()));
    }

    Result<Object> routeingRR(String sign, Object[] args) {
        final String domain = sign.substring(0, sign.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Optional<Remote> sockets = findRemoteServiceOptional(domain);
        if (!sockets.isPresent()) {
            log.error("[{}] not found remote service of {}[{}]  with routes {}", name, sign, domain, super.remoteDomains);
            throw new IllegalStateException("not exists service for '" + sign + "' in " + name);
        }
        final Remote remote = sockets.get();
        return onDebugWithTimerReturns(
            log -> log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} .", name, sign, args)
            , null
            , log -> {
                final Payload result = remote.socket.requestResponse(Request.build(sign, name, args, debug.get() || trace.get())).block(timeout.get());
                if ((debug.get() || trace.get()) && result != null) {
                    final Meta meta = Response.parseMeta(result);
                    log.info("[{}] remote trace \n DOMAIN: {} \n ARGUMENTS: {} \n META: {}", name, sign, args, meta);
                }
                if (result == null) {
                    log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", name, sign, args, remote);
                    return Result.error(new IllegalAccessError("error to call remote service " + remote.name + " from " + name));
                }
                final Response response = Response.parse(result);
                log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", name, sign, args, response, remote);
                return response.response;
            }
        );
    }
    //endregion

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTimeout(@Nullable Duration timeout) {
        this.timeout.set(timeout);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setDebug(boolean debug) {
        this.debug.set(debug);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setTrace(boolean trace) {
        this.trace.set(trace);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void release() {
        super.purify();
    }

    //region Client And Server Builders
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

    private static Resume buildResume(Config.ResumeSetting config, boolean client) {
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
    //endregion

    /**
     * {@inheritDoc}
     */
    @Override
    public void startClient(String name, Config.ClientConfig config) {
        if (servers.containsKey(name))
            throw new IllegalStateException("a service or client with name is already exists ! name is " + name);
        final RSocketConnector builder = RSocketConnector.create()
            .reconnect(buildRetry(config.getRetry()))
            .payloadDecoder(config.getPayloadCodec() == 1 ? PayloadDecoder.DEFAULT : PayloadDecoder.ZERO_COPY)
            .keepAlive(
                config.getKeepAliveInterval() == null ? Duration.ofSeconds(20) : config.getKeepAliveInterval(),
                config.getKeepAliveMaxLifeTime() == null ? Duration.ofSeconds(90) : config.getKeepAliveMaxLifeTime()
            )
            .setupPayload(DefaultPayload.create(Proto.to(config.getResume() != null))) //current setup from client is support resume or not
            .acceptor((setup, sending) -> {
                final Remote remote = Remote.builder()
                    .idx(-servers.size())
                    .socket(sending)
                    .build();
                if (debug.get()) {
                    log.debug("remote connect to client [{}]", name);
                }
                final ServiceRSocket rSocket = new ServiceRSocket(name, remote, false);
                remote.setServer(rSocket);
                addOrUpdateRemote(remote, null, false);
                if (!services.isEmpty()) pushMeta(null, remote);
                return Mono.just(rSocket);
            });
        if (config.getFragment() != null) builder.fragment(config.getFragment());
        if (config.getMaxInboundPayloadSize() != null) builder.maxInboundPayloadSize(config.getMaxInboundPayloadSize());
        if (config.getResume() != null) builder
            .resume(buildResume(config.getResume(), true));
        builder.connect(buildTransport(config))
            .subscribe(client -> {
                log.debug("rpc client [{}] started", name);
                addServer(client, name);
            });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startServer(String name, Config.ServerConfig config) {
        if (servers.containsKey(name))
            throw new IllegalStateException("a service or client with name is already exists ! name is " + name);
        final ServerTransport<? extends Closeable> transport = buildTransport(config);
        final RSocketServer builder = RSocketServer.create()
            .payloadDecoder(config.getPayloadCodec() == 1 ? PayloadDecoder.DEFAULT : PayloadDecoder.ZERO_COPY)
            .acceptor((setup, sending) -> {
                //current setup from client is support resume or not
                Boolean resume = Proto.from(ByteBufUtil.getBytes(setup.sliceData()), Boolean.class);
                final Remote remote = Remote.builder()
                    .idx(-servers.size())
                    .name("UNK")
                    .socket(sending)
                    .resume(resume)
                    .build();
                log.debug("remote connect to server [{}]", name);
                final ServiceRSocket rSocket = new ServiceRSocket(name, remote, true);
                remote.setServer(rSocket);
                addOrUpdateRemote(remote, null, false);
                if (!services.isEmpty()) pushMeta(null, remote);
                return Mono.just(rSocket);
            });
        if (config.getResume() != null) builder.resume(buildResume(config.getResume(), false));
        if (config.getFragment() != null) builder.fragment(config.getFragment());
        if (config.getMaxInboundPayloadSize() != null) builder.maxInboundPayloadSize(config.getMaxInboundPayloadSize());
        builder.bind(transport)
            .subscribe(server -> {
                if (debug.get()) {
                    log.debug("server [{}] started on {}", name, transport);
                }
                addServer(server, name);
            });
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T createClientService(Class<T> clientKlass, @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor) {
        return (T) clientCreator.create(clientKlass, argumentProcessor);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerService(T service, Class<T> serviceKlass, @Nullable Map<String, Function<Object, Object>> resultProcessor) {
        serviceRegister.register(service, serviceKlass, resultProcessor);
        syncServMeta();
    }


    @Override
    public @Unmodifiable List<String> names() {
        return Collections.unmodifiableList(new ArrayList<>(servers.keySet()));
    }

    @Override
    public boolean dispose(@NotNull String name) {
        if (servers.containsKey(name)) {
            servers.get(name).dispose();
            servers.remove(name);
            return true;
        }
        return false;
    }

    @Override
    public Payload getServMeta(Remote remote) {
        onDebug(log -> log.warn("[{}] will generate ServMeta of {}", name, routes.get()));
        return ServMeta.build(name, routes.get(), remote.service);
    }


    final class ServiceRSocket implements RSocket {
        public final AtomicReference<Remote> serviceRef = new AtomicReference<>();
        public final String server;
        public final boolean serverMode;

        ServiceRSocket(String server, Remote remote, boolean mode) {
            this.server = server;
            this.serverMode = mode;
            this.serviceRef.set(remote);
        }

        @Override
        public @NotNull Mono<Void> fireAndForget(@NotNull Payload payload) {
            onDebug(log -> log.debug("[{}] {} on FireAndForget {}", name, server, dump(serviceRef.get(), payload)));
            handleFNF(payload, serviceRef.get());
            return Mono.empty();
        }

        @Override
        public @NotNull Mono<Payload> requestResponse(@NotNull Payload payload) {
            onDebug(log -> log.debug("[{}] {} on RequestResponse {}", name, server, dump(serviceRef.get(), payload)));
            return handleRR(payload, serviceRef.get());
        }

        @Override
        public @NotNull Mono<Void> metadataPush(@NotNull Payload payload) {
            final ServMeta servMeta = ServMeta.parse(payload);
            onDebug(log -> log.debug("[{}] {} on metadataPush with \n SERV_META: {} ", name, server, servMeta));

            servMetaProcess(servMeta, serviceRef.get());
            return Mono.empty();
        }

        public void removeRegistry() {
            remotes.remove(serviceRef.get().idx);
            remoteServices.forEach((k, v) -> v.remove(serviceRef.get()));
        }

        @Override
        public @NotNull Mono<Void> onClose() {
            removeRegistry();
            return Mono.empty();
        }

        @Override
        public String toString() {
            return "ServiceRSocket{" +
                "serviceRef=" + serviceRef.get() +
                ", serverName='" + server + '\'' +
                ", server=" + serverMode +
                '}';
        }
    }

}
