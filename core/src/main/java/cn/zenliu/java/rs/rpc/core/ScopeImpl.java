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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import org.jooq.lambda.Seq;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.zenliu.java.rs.rpc.core.ProxyUtil.clientCreatorBuilder;
import static cn.zenliu.java.rs.rpc.core.ProxyUtil.serviceRegisterBuilder;
import static cn.zenliu.java.rs.rpc.core.Service.NONE_META_NAME;


/**
 * Scope implement
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */

@Slf4j
public final class ScopeImpl implements Scope, Serializable {
    private static final long serialVersionUID = -7734615300814212060L;


    static String LOG_META = "\n META: {}\n SERVICE: {}";
    static String LOG_META_REQUEST = "\n META:{}\n REQUEST: {}\n SERVICE: {}";
    @Getter final String name;
    @Getter final boolean route;
    transient final Supplier<Payload> servMetaBuilder;
    transient final ClientCreator clientCreator;
    @Getter private final AtomicBoolean debug = new AtomicBoolean(false);
    @Getter private final AtomicReference<Duration> timeout = new AtomicReference<>(Duration.ofSeconds(2));
    @Getter private final Map<String, Disposable> localServers = new ConcurrentHashMap<>();
    @Getter private final Set<String> localServices = new HashSet<>();
    @Getter private final AtomicReference<Set<String>> routes = new AtomicReference<>();
    @Getter private final Map<String, Function<Object[], Result<Object>>> handler = new ConcurrentHashMap<>();
    transient final ServiceRegister serviceRegister = serviceRegisterBuilder(localServices, handler);
    @Getter private final Map<String, TreeSet<Service>> remoteServices = new ConcurrentHashMap<>();
    @Getter private final Map<Integer, Service> remoteRegistry = new ConcurrentHashMap<>();
    @Getter private final List<String> remoteNames = new CopyOnWriteArrayList<>();


    @Builder
    public ScopeImpl(String name, boolean route) {
        this.name = name == null || name.isEmpty() ? UUID.randomUUID().toString() : name;
        servMetaBuilder = () -> ServMeta.build(name, routes.get());
        clientCreator = clientCreatorBuilder(name, this::forgetRouting, this::requestRouting);
        this.route = route;
        calcRoutes();
    }

    static String dump(Service service, Payload payload) {
        return "\n SERVICE: " + service.name + " | SERVICES:" + service.service + "| WEIGHT:" + service.weight + "\n" +
            " META :\n" + ByteBufUtil.prettyHexDump(payload.sliceMetadata()) +
            " DATA :\n" + ByteBufUtil.prettyHexDump(payload.sliceData());
    }

    private void calcRoutes() {
        Set<String> routes = new HashSet<>(localServices);
        if (route) routes.addAll(Seq.seq(remoteServices.keySet()).map(x -> x + "?").toSet());
        this.routes.set(routes);
    }

    /**
     * register local server|client
     *
     * @param name    local server name
     * @param service the instance as Disposable
     */
    void addLocal(Disposable service, String name) {
        localServers.put(name, service);
    }

    /**
     * process ServiceMeta Request
     *
     * @param meta    payload
     * @param service current Meta
     */
    void metaProcess(ServMeta meta, Service service) {
        final Service newService = service.updateFromMeta(meta);
        addOrUpdateRemote(newService, service);
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
     * Sync Meta data;
     */
    private void syncServMeta() {
        calcRoutes();
        if (remoteRegistry.isEmpty()) return;
        final Payload meta = servMetaBuilder.get();
        if (debug.get()) log.debug("will sync serv meta {} to remote {}  ", this.routes.get(), remoteRegistry);
        remoteRegistry.forEach((i, v) -> {
            if (debug.get()) log.debug(" sync serv meta to remote[{}]: {} ", v, this.routes.get());
            pushMeta(meta, v);
        });
    }

    /**
     * generate index by Remote Name
     *
     * @param name Remote Name
     * @return Index or -1
     */
    private int confirmRemote(String name) {
        if (remoteNames.contains(name)) {
            log.debug("already exists remote name of {}", name);
            return remoteNames.indexOf(name);
        }
        final int newIdx = remoteNames.size();
        remoteNames.add(name);
        return newIdx;
    }


    /**
     * update meta in RemotesRegistry
     *
     * @param service new Meta
     * @param old     old Meta
     */
    private void updateRemoteService(Service service, Service old) {
        for (String serv : service.service) {
            if (!remoteServices.containsKey(serv)) {
                remoteServices.put(serv, new TreeSet<>(Service.weightComparator));
            }
            remoteServices.get(serv).add(service);
        }
        if (old != null) {
            old.service.forEach(v -> remoteServices.get(v).remove(old));
        }
        calcRoutes();
    }

    /**
     * current is not support metadata push with Resume enabled!
     * <b>note:</b>
     * Should METADATA_PUSH should be part of resumption? #235
     */
    private void pushMeta(@Nullable Payload meta, Service service) {
        service.pushMeta(meta == null ? servMetaBuilder.get() : meta);
    }

    /**
     * Process FireAndForgot
     *
     * @param p       payload
     * @param service meta of remote
     */
    void handleFNF(Payload p, Service service) {
        log.debug("service {}  received  FNF", service.name);
        if (p.data().capacity() == 0) { // a meta push must without data
            log.debug("service {} maybe received meta via FNF", service.name);
            ServMeta result = service.tryHandleMeta(p);
            if (result != null) {
                metaProcess(result, service);
                return;
            }
        }

        final Meta meta = Request.parseMeta(p);
        log.debug("[{}] begin to process FireAndForget:" + LOG_META, name, meta, service);
        if (handler.containsKey(meta.domain)) {
            final Request request = Request.parseRequest(p);
            if (debug.get()) {
                log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST, name, meta, request, service);
                long st = System.nanoTime();
                try {
                    handler.get(meta.domain).apply(request.arguments);
                } catch (Exception e) {
                    log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, name, meta, request, service, e);
                } finally {
                    long et = System.nanoTime();
                    log.debug("[{}] process FireAndForget:" + LOG_META_REQUEST + "\n LOCAL_COST: {} μs;", name, meta, request, service, (et - st) / 1000.0);
                }
            } else {
                try {
                    handler.get(meta.domain).apply(request.arguments);
                } catch (Exception e) {
                    log.error("[{}] error to process FireAndForget:" + LOG_META_REQUEST, name, meta, request, service, e);
                }
            }

        } else //found routeing services
            if (remoteServices.containsKey(meta.domain + "?")) {
                final Service routeNode = remoteServices.get(meta.domain + "?").first();
                if (debug.get())
                    log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, service, routeNode);
                else
                    log.debug("[{}] routeing FireAndForget:" + LOG_META + "\n NEXT:{}", name, meta, service.name, routeNode.name);
                routeNode.socket.fireAndForget(Request.updateMeta(p, meta, name));
            } else {
                log.warn("[{}] none registered FireAndForget:" + LOG_META, name, meta, service);
            }
    }


    void forgetRouting(String domain, Object[] args) {
        final Optional<Service> sockets = routing(domain);
        if (!sockets.isPresent()) {
            throw new IllegalStateException("not exists service for " + domain);
        }
        final Service service = sockets.get();
        service.socket.fireAndForget(Request.build(domain, name, args));
    }

    Result<Object> requestRouting(String domain, Object[] args) {
        final Optional<Service> sockets = routing(domain);
        if (!sockets.isPresent()) {
            throw new IllegalStateException("not exists service for " + domain);
        }
        final Service service = sockets.get();
        if (debug.get()) {
            log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} .", name, domain, args);
            long ts = System.currentTimeMillis();
            final Payload result = service.socket.requestResponse(Request.build(domain, name, args)).block(timeout.get());
            log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n TOTAL COST {}", name, domain, args, (System.currentTimeMillis() - ts));
            if (result == null) {
                log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", name, domain, args, service);
                return Result.error(new IllegalAccessError("error to call remote service " + service.name + " from " + name));
            }
            final Response response = Response.parse(result);
            log.debug("[{}] remote call \n DOMAIN: {} \n ARGUMENTS: {} \n RESULT: {} \n SERVICE:{}", name, domain, args, response, service);
            return response.response;
        }
        final Payload result = service.socket.requestResponse(Request.build(domain, name, args)).block(timeout.get());
        if (result == null) {
            log.error("[{}] error to request,got null result. \n DOMAIN:: {} \n ARGUMENTS: {} \n SERVICE {}", name, domain, args, service);
            return Result.error(new IllegalAccessError("error to call remote service " + service.name + " from " + name));
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
        localServers.forEach((i, s) -> {
            if (!s.isDisposed()) {
                s.dispose();
            }
        });
        remoteRegistry.forEach((k, x) -> {
            if (!x.socket.isDisposed()) {
                x.socket.dispose();
            }
        });

        localServices.clear();
        localServers.clear();

        remoteNames.clear();
        remoteServices.clear();
        remoteRegistry.clear();

        handler.clear();
    }

    /**
     * RequestResponse
     *
     * @param p       payload
     * @param service meta of remote
     * @return response Payload
     */
    Mono<Payload> handleRR(Payload p, Service service) {
        final Meta meta = Request.parseMeta(p);
        log.debug("[{}] process RequestAndResponse:" + LOG_META, name, meta, service);
        if (handler.containsKey(meta.domain)) {
            try {
                final Request request = Request.parseRequest(p);
                final Result<Object> result;
                if (debug.get()) {
                    log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST, name, meta, request, service);
                    long st = System.nanoTime();
                    result = handler.get(meta.domain).apply(request.arguments);
                    long et = System.nanoTime();
                    log.debug("[{}] process RequestAndResponse:" + LOG_META_REQUEST + "LOCAL_COST: {} μs \nRESPONSE: {}", name, meta, request, service, (et - st) / 1000.0, result);
                } else result = handler.get(meta.domain).apply(request.arguments);
                return Mono.just(Response.build(meta, name, result != null ? result : Result.ok(null)));
            } catch (Exception e) {
                log.debug("[{}] error on process RequestAndResponse:" + LOG_META, name, meta, service, e);
                return Mono.just(Response.build(meta, name, Result.error(e)));
            }
        } else //do routeing
            if (remoteServices.containsKey(meta.domain + "?")) {
                try {
                    final Service routeNode = remoteServices.get(meta.domain + "?").first();
                    if (debug.get())
                        log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, service, routeNode);
                    else
                        log.debug("[{}] routeing RequestAndResponse:" + LOG_META + "\n NEXT:{}", name, meta, service.name, routeNode.name);
                    return routeNode.socket.requestResponse(Request.updateMeta(p, meta, name));
                } catch (Exception e) {
                    log.debug("[{}] error on process Routeing RequestAndResponse:" + LOG_META, name, meta, service, e);
                    return Mono.just(Response.build(meta, name, Result.error(e)));
                }
            }
        {
            log.debug("[{}] none registered RequestAndResponse:" + LOG_META, name, meta, service);
            return Mono.just(Response.build(meta, name, Result.error(new IllegalStateException("no such method on " + name))));
        }

    }

    /**
     * routing is use for Delegator service
     */
    Optional<Service> routing(String domain) {
        final String service = domain.substring(0, domain.indexOf('#'));
        if (remoteServices.containsKey(service)) {
            final TreeSet<Service> socket = remoteServices.get(service);
            if (socket.isEmpty()) {
                remoteServices.remove(service + "?");
                log.debug("not exists service for {} on {} with {}", domain, name, service);
                return Optional.empty();
            }
            return Optional.of(socket.first());
        } else if (remoteServices.containsKey(service + "?")) {
            final TreeSet<Service> socket = remoteServices.get(service + "?");
            if (socket.isEmpty()) {
                remoteServices.remove(service + "?");
                log.debug("not exists routeing service for {} on {} with {}", domain, name, service);
                return Optional.empty();
            }
            return Optional.of(socket.first());
        } else {
            return Optional.empty();
        }
    }

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

    /**
     * add or update Meta
     *
     * @param service    the remote meta
     * @param oldService the old meta
     */
    void addOrUpdateRemote(Service service, Service oldService) {
        if (oldService == null && service.name.equals(NONE_META_NAME)) {
            if (debug.get()) {
                log.debug("a new connection found {} \n sync serv meta :{}", service, localServices);
            }
            remoteRegistry.put(service.idx, service);
            pushMeta(null, service);
            return;
        }
        final int remoteIdx = oldService.idx >= 0 ? oldService.idx : confirmRemote(service.name);
        if (oldService.getName().equals(NONE_META_NAME)) remoteRegistry.remove(oldService.idx);
        if (remoteRegistry.containsKey(remoteIdx)) {
            service.setIdx(remoteIdx);
            final Service olderService = remoteRegistry.get(remoteIdx);
            if (service.idx == -1) service.setIdx(remoteIdx);
            if (service.socket == null) service.setSocket(olderService.socket);
            if (service.weight == 0) service.setWeight(Math.max(olderService.weight, 1));
            log.debug("update meta for remote {}[{}] ", service, remoteIdx);
            remoteRegistry.put(remoteIdx, service);
            log.debug("remove and update meta from {}  to {}", oldService, service);
            updateRemoteService(service, olderService);
        } else {
            log.debug("new meta for remote {}[{}] ", service, remoteIdx);
            if (service.idx == -1) service.setIdx(remoteIdx);
            if (service.weight == 0) service.setWeight(1);
            remoteRegistry.put(remoteIdx, service);
            updateRemoteService(service, oldService);
            // meta.socket.metadataPush(metaBuilder.get()).subscribe();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startClient(String name, Config.ClientConfig config) {
        if (localServers.containsKey(name))
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
                final Service service = Service.builder()
                    .idx(remoteRegistry.size() + 1)
                    .socket(sending)
                    .build();
                if (debug.get()) {
                    log.debug("remote connect to client [{}]", name);
                }
                final ServiceRSocket rSocket = new ServiceRSocket(name, service, false);
                service.setServer(rSocket);
                addOrUpdateRemote(service, null);
                return Mono.just(rSocket);
            });
        if (config.getFragment() != null) builder.fragment(config.getFragment());
        if (config.getMaxInboundPayloadSize() != null) builder.maxInboundPayloadSize(config.getMaxInboundPayloadSize());
        if (config.getResume() != null) builder
            .resume(buildResume(config.getResume(), true));
        builder.connect(buildTransport(config))
            .subscribe(client -> {
                log.debug("rpc client [{}] started", name);
                addLocal(client, name);
            });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startServer(String name, Config.ServerConfig config) {
        if (localServers.containsKey(name))
            throw new IllegalStateException("a service or client with name is already exists ! name is " + name);
        final ServerTransport<? extends Closeable> transport = buildTransport(config);
        final RSocketServer builder = RSocketServer.create()
            .payloadDecoder(config.getPayloadCodec() == 1 ? PayloadDecoder.DEFAULT : PayloadDecoder.ZERO_COPY)
            .acceptor((setup, sending) -> {
                //current setup from client is support resume or not
                Boolean resume = Proto.from(ByteBufUtil.getBytes(setup.sliceData()), Boolean.class);
                final Service service = Service.builder()
                    .idx(remoteRegistry.size() + 1)
                    .name("UNK")
                    .socket(sending)
                    .resume(resume)
                    .build();
                log.debug("remote connect to server [{}]", name);
                final ServiceRSocket rSocket = new ServiceRSocket(name, service, true);
                service.setServer(rSocket);
                addOrUpdateRemote(service, null);
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
                addLocal(server, name);
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
        return Collections.unmodifiableList(new ArrayList<>(localServers.keySet()));
    }

    @Override
    public boolean dispose(@NotNull String name) {
        if (localServers.containsKey(name)) {
            localServers.get(name).dispose();
            localServers.remove(name);
            return true;
        }
        return false;
    }

    final class ServiceRSocket implements RSocket {
        public
        final AtomicReference<Service> serviceRef = new AtomicReference<>();
        public final String serverName;
        public final boolean server;

        ServiceRSocket(String serverName, Service service, boolean isServer) {
            this.serverName = serverName;
            this.server = isServer;
            this.serviceRef.set(service);
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
            metaProcess(servMeta, serviceRef.get());
            return Mono.empty();
        }

        public void removeRegistry() {
            remoteRegistry.remove(serviceRef.get().idx);
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
                ", serverName='" + serverName + '\'' +
                ", server=" + server +
                '}';
        }
    }

}
