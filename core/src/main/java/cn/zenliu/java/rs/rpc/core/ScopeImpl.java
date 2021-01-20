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
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.Supplier;

import static cn.zenliu.java.rs.rpc.core.ProxyUtil.clientCreatorBuilder;
import static cn.zenliu.java.rs.rpc.core.ProxyUtil.serviceRegisterBuilder;
import static cn.zenliu.java.rs.rpc.core.Remote.NONE_META_NAME;


/**
 * Scope implement
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */

@Slf4j
public final class ScopeImpl extends ScopeContext implements Scope, Serializable {
    private static final long serialVersionUID = -7734615300814212060L;


    static String LOG_META = "\n META: {}\n SERVICE: {}";
    static String LOG_META_REQUEST = "\n META:{}\n REQUEST: {}\n SERVICE: {}";


    transient final Supplier<Payload> servMetaBuilder;
    transient final ClientCreator clientCreator;
    transient final ServiceRegister serviceRegister = serviceRegisterBuilder(this::addService, this::addHandler);


    @Builder
    public ScopeImpl(String name, boolean route) {
        super(name == null || name.isEmpty() ? UUID.randomUUID().toString() : name, route);
        servMetaBuilder = () -> ServMeta.build(name, routes.get());
        clientCreator = clientCreatorBuilder(name, this::routeingFNF, this::routeingRR);
        calcRoutes();
    }

    static String dump(Remote remote, Payload payload) {
        return "\n SERVICE: " + remote.name + " | SERVICES:" + remote.service + "| WEIGHT:" + remote.weight + "\n" +
            " META :\n" + ByteBufUtil.prettyHexDump(payload.sliceMetadata()) +
            " DATA :\n" + ByteBufUtil.prettyHexDump(payload.sliceData());
    }


    //region Service And ServMeta processors

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

    /**
     * process ServiceMeta Request
     *
     * @param meta   payload
     * @param remote current Meta
     */
    void servMetaProcess(ServMeta meta, Remote remote) {
        final Remote newRemote = remote.updateFromMeta(meta);
        addOrUpdateRemote(newRemote, remote);
    }

    /**
     * Sync Meta data: push meta to all remotes
     */
    private void syncServMeta(Remote... except) {
        if (remotes.isEmpty()) return;
        final Payload meta = servMetaBuilder.get();
        if (debug.get()) log.debug("will sync serv meta {} to remote {}  ", this.routes.get(), remotes);
        remotes.forEach((i, v) -> {
            for (Remote remote : except) {
                if (remote == v) return;
            }
            if (debug.get()) log.debug(" sync serv meta to remote[{}]: {} ", v, this.routes.get());
            pushMeta(meta, v);
        });
    }

    /**
     * current is not support metadata push with Resume enabled!
     * <b>note:</b>
     * Should METADATA_PUSH should be part of resumption? #235
     */
    private void pushMeta(@Nullable Payload meta, Remote remote) {
        remote.pushMeta(meta == null ? servMetaBuilder.get() : meta);
    }
    //endregion

    //region RSocket Handlers

    /**
     * add or update Meta
     *
     * @param remote    the remote meta
     * @param oldRemote the old meta
     */
    void addOrUpdateRemote(Remote remote, Remote oldRemote) {
        if (oldRemote == null && remote.name.equals(NONE_META_NAME)) {
            if (debug.get()) {
                log.debug("a new connection found {} \n sync serv meta :{}", remote, remote);
            }
            remotes.put(remote.idx, remote);
            pushMeta(null, remote);
            return;
        }
        final int remoteIdx = oldRemote.idx >= 0 ? oldRemote.idx : prepareRemoteName(remote.name);
        if (oldRemote.getName().equals(NONE_META_NAME)) remotes.remove(oldRemote.idx);
        if (remotes.containsKey(remoteIdx)) {
            remote.setIdx(remoteIdx);
            final Remote olderRemote = remotes.get(remoteIdx);
            if (remote.idx == -1) remote.setIdx(remoteIdx);
            if (remote.socket == null) remote.setSocket(olderRemote.socket);
            if (remote.weight == 0) remote.setWeight(Math.max(olderRemote.weight, 1));
            log.debug("update meta for remote {}[{}] ", remote, remoteIdx);
            remotes.put(remoteIdx, remote);
            log.debug("remove and update meta \n FROM {} \nTO {}", oldRemote, remote);
            updateRemoteService(remote, olderRemote);
            if (!olderRemote.getName().equals(NONE_META_NAME)) syncServMeta(remote);
        } else {
            log.debug("new meta for remote {}[{}] ", remote, remoteIdx);
            if (remote.idx == -1) remote.setIdx(remoteIdx);
            if (remote.weight == 0) remote.setWeight(1);
            remotes.put(remoteIdx, remote);
            updateRemoteService(remote, oldRemote);
            syncServMeta(remote);
            // meta.socket.metadataPush(metaBuilder.get()).subscribe();
        }
    }

    /**
     * Process FireAndForgot
     *
     * @param p      payload
     * @param remote meta of remote
     */
    void handleFNF(Payload p, Remote remote) {
        log.debug("service {}  received  FNF", remote.name);
        if (p.data().capacity() == 0) { // a meta push must without data
            log.debug("service {} maybe received meta via FNF", remote.name);
            ServMeta result = remote.tryHandleMeta(p);
            if (result != null) {
                servMetaProcess(result, remote);
                return;
            }
        }

        final Meta meta = Request.parseMeta(p);
        log.debug("[{}] begin to process FireAndForget:" + LOG_META, name, meta, remote);
        final boolean haveHandler = findHandlerOptional(meta.domain).map(handler -> {
            final Request request = Request.parseRequest(p);
            if (debug.get()) {
                log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST, name, meta, request, remote);
                long st = System.nanoTime();
                try {
                    handler.apply(request.arguments);
                } catch (Exception e) {
                    log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, name, meta, request, remote, e);
                } finally {
                    long et = System.nanoTime();
                    log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST + "\n LOCAL_COST: {} μs;", name, meta, request, remote, (et - st) / 1000.0);
                }
            } else {
                try {
                    handler.apply(request.arguments);
                } catch (Exception e) {
                    log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, name, meta, request, remote, e);
                }
            }
            return 0;
        }).isPresent();
        if (route && !haveHandler) {
            final Remote service = findRemoteService(meta.domain);
            if (service != null) {
                if (debug.get())
                    log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, remote, service);
                else
                    log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, remote.name, service.name);
                service.socket.fireAndForget(Request.updateMeta(p, meta, name));
                return;
            }
        }
        log.warn("[{}] none registered FireAndForget:" + LOG_META, name, meta, remote);
    }
    //endregion

    /**
     * RequestResponse
     *
     * @param p      payload
     * @param remote remote
     * @return response Payload
     */
    Mono<Payload> handleRR(Payload p, Remote remote) {
        final Meta meta = Request.parseMeta(p);
        log.debug("[{}] process RequestAndResponse:" + LOG_META, name, meta, remote);
        final Mono<Payload> result = findHandlerOptional(meta.domain).map(handler -> {
            try {
                final Request request = Request.parseRequest(p);
                final Result<Object> res;
                if (debug.get()) {
                    log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST, name, meta, request, remote);
                    long st = System.nanoTime();
                    res = handler.apply(request.arguments);
                    long et = System.nanoTime();
                    log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST + "LOCAL_COST: {} μs \nRESPONSE: {}", name, meta, request, remote, (et - st) / 1000.0, res);
                } else res = handler.apply(request.arguments);
                return Mono.just(Response.build(meta, name, res != null ? res : Result.ok(null)));
            } catch (Exception e) {
                log.debug("[{}] error on process RequestAndResponse:" + LOG_META, name, meta, remote, e);
                return Mono.just(Response.build(meta, name, Result.error(e)));
            }
        }).orElseGet(() -> {
            if (!route) return null;
            final Remote service = findRemoteService(meta.domain);
            if (service == null) return null;
            if (debug.get())
                log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, remote, service);
            else
                log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, remote.name, service.name);
            return service.socket.requestResponse(Request.updateMeta(p, meta, name));
        });
        if (result == null) {
            log.debug("[{}] none registered RequestAndResponse:" + LOG_META, name, meta, remote);
            return Mono.just(Response.build(meta, name, Result.error(new IllegalStateException("no such method on " + name))));
        }
        return result;
    }

    //region Service Call Routeing
    void routeingFNF(String handlerSignature, Object[] args) {
        final String domain = handlerSignature.substring(0, handlerSignature.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Optional<Remote> sockets = findRemoteServiceOptional(domain);
        if (!sockets.isPresent()) {
            throw new IllegalStateException("not exists service for " + handlerSignature);
        }
        final Remote remote = sockets.get();
        remote.socket.fireAndForget(Request.build(handlerSignature, name, args));
    }


    //endregion

    Result<Object> routeingRR(String handlerSignature, Object[] args) {
        final String domain = handlerSignature.substring(0, handlerSignature.indexOf(ProxyUtil.DOMAIN_SPLITTER));
        final Optional<Remote> sockets = findRemoteServiceOptional(domain);
        if (!sockets.isPresent()) {
            throw new IllegalStateException("not exists service for '" + handlerSignature + "' in " + name + " with " + super.dump());
        }
        final Remote remote = sockets.get();
        if (debug.get()) {
            log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} .", name, handlerSignature, args);
            long ts = System.currentTimeMillis();
            final Payload result = remote.socket.requestResponse(Request.build(handlerSignature, name, args)).block(timeout.get());
            if (debug.get() && result != null) {
                final Meta meta = Response.parseMeta(result);
                log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n TOTAL COST {} ms \n META: {}", name, handlerSignature, args, (System.currentTimeMillis() - ts), meta);
            }
            if (result == null) {
                log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", name, handlerSignature, args, remote);
                return Result.error(new IllegalAccessError("error to call remote service " + remote.name + " from " + name));
            }
            final Response response = Response.parse(result);
            log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", name, handlerSignature, args, response, remote);
            return response.response;
        }
        final Payload result = remote.socket.requestResponse(Request.build(handlerSignature, name, args)).block(timeout.get());
        if (result == null) {
            log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", name, handlerSignature, args, remote);
            return Result.error(new IllegalAccessError("error to call remote service " + remote.name + " from " + name));
        }
        final Response response = Response.parse(result);
        return response.response;
    }

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
    public void release() {
        super.purify();
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
                addOrUpdateRemote(remote, null);
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
                addOrUpdateRemote(remote, null);
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
            if (debug.get()) {
                log.debug("on FireAndForget {}", dump(serviceRef.get(), payload));
            }
            handleFNF(payload, serviceRef.get());
            return Mono.empty();
        }

        @Override
        public @NotNull Mono<Payload> requestResponse(@NotNull Payload payload) {
            if (debug.get()) {
                log.debug("on requestResponse {}", dump(serviceRef.get(), payload));
            }
            return handleRR(payload, serviceRef.get());
        }

        @Override
        public @NotNull Mono<Void> metadataPush(@NotNull Payload payload) {
            final ServMeta servMeta = ServMeta.parse(payload);
            log.debug("[{}]process meta for MetadataPush in \n SERV_META: {} \n SERVICE: {}", name, servMeta, serviceRef.get());
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
