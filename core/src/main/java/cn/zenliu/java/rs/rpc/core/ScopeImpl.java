package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.ProxyUtil.ClientCreator;
import cn.zenliu.java.rs.rpc.core.ProxyUtil.ServiceRegister;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.zenliu.java.rs.rpc.core.ProxyUtil.clientCreatorBuilder;
import static cn.zenliu.java.rs.rpc.core.ProxyUtil.serviceRegisterBuilder;


/**
 * Scope implement
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */


public final class ScopeImpl extends ScopeContextImpl implements Scope, Serializable {
    private static final long serialVersionUID = -7734615300814212060L;

    transient final ClientCreator clientCreator = clientCreatorBuilder(this, this::routeingFNF, this::routeingRR);
    transient final ServiceRegister serviceRegister = serviceRegisterBuilder(this, this::addHandler);


    @Builder
    public ScopeImpl(String name, boolean route) {
        super(name == null || name.isEmpty() ? UUID.randomUUID().toString() : name, route);
    }

    static String dump(Remote remote, Payload payload) {
        return "\n SERVICE: " + remote.name + " | SERVICES:" + remote.service + "| WEIGHT:" + remote.weight + "\n" +
            " META :\n" + ByteBufUtil.prettyHexDump(payload.sliceMetadata()) +
            "\n DATA :\n" + ByteBufUtil.prettyHexDump(payload.sliceData());
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


    /**
     * {@inheritDoc}
     */
    @Override
    public void startClient(String name, Config.ClientConfig config) {
        if (servers.containsKey(name))
            throw new IllegalStateException("a service or client with name is already exists ! name is " + name);

        final Supplier<Mono<? extends Closeable>> clientSupplier = RSocketUtil
            .buildClient((setup, sending) -> {
                    final Remote remote = Remote.builder()
                        .idx(-getRemoteNames().size())
                        .socket(sending)
                        .build();
                    if (debug.get()) {
                        log.debug("remote connect to client [{}]", name);
                    }
                    final ServiceRSocket rSocket = new ServiceRSocket(name, remote, false);
                    remote.setServer(rSocket);
                    processRemoteUpdate(remote, null, false);
                    return Mono.just(rSocket);
                }, resume -> Mono.fromCallable(() -> DefaultPayload.create(Proto.to(resume)))
                , config);
        if (config.getConnectRetry() != null && config.getResume() == null) {
            final Retry retry = RSocketUtil.buildRetry(config.getRetry());
            clientSupplier.get()
                .retryWhen(retry)
                .subscribe(client -> {
                    log.debug("rpc client [{}] started", name);
                    addServer(client, name);
                });
        } else {
            clientSupplier.get()
                .subscribe(client -> {
                    log.debug("rpc client [{}] started", name);
                    addServer(client, name);
                });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startServer(String name, Config.ServerConfig config) {
        if (servers.containsKey(name))
            throw new IllegalStateException("a service or client with name is already exists ! name is " + name);
        final Supplier<Mono<? extends Closeable>> serverSupplier = RSocketUtil.buildServer((setup, sending) -> {
            //current setup from client is support resume or not
            Boolean resume = Proto.from(ByteBufUtil.getBytes(setup.sliceData()), Boolean.class);
            final Remote remote = Remote.builder()
                .idx(-getRemoteNames().size())
                .name("UNK")
                .socket(sending)
                .resume(resume)
                .build();
            log.debug("remote connect to server [{}]", name);
            final ServiceRSocket rSocket = new ServiceRSocket(name, remote, true);
            remote.setServer(rSocket);
            processRemoteUpdate(remote, null, false);
            return Mono.just(rSocket);
        }, config);
        serverSupplier.get().subscribe(server -> {
            info("started on {}:{}", config.getBindAddress() == null ? "0.0.0.0" : config.getBindAddress(), config.getPort());
            addServer(server, name);
        });
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("unchecked")
    @Override
    public <T> T createClientService(Class<T> clientKlass, @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor, boolean useFNF) {
        return (T) clientCreator.create(clientKlass, argumentProcessor, useFNF);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> void registerService(T service, Class<T> serviceKlass, @Nullable Map<String, Function<Object, Object>> resultProcessor) {
        serviceRegister.register(service, serviceKlass, resultProcessor);
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
        public final AtomicReference<Remote> remoteRef = new AtomicReference<>();
        public final String server;
        public final boolean serverMode;

        ServiceRSocket(String server, Remote remote, boolean mode) {
            this.server = server;
            this.serverMode = mode;
            this.remoteRef.set(remote);
        }

        @Override
        public @NotNull Mono<Void> fireAndForget(@NotNull Payload payload) {
            onDebug("{} on FireAndForget {} ", server, dump(remoteRef.get(), payload));
            return FunctorPayload.fnfHandler(payload, remoteRef.get(), ScopeImpl.this::onServMeta, ScopeImpl.this::onFNF);
        }

        @Override
        public @NotNull Mono<Payload> requestResponse(@NotNull Payload payload) {
            onDebug("{} on RequestResponse {}", server, dump(remoteRef.get(), payload));
            return FunctorPayload.rrHandler(payload, remoteRef.get(), ScopeImpl.this::onRR);
        }

        @Override
        public @NotNull Mono<Void> metadataPush(@NotNull Payload payload) {
            onDebug("{} on MetadataPush {}", server, dump(remoteRef.get(), payload));
            return FunctorPayload.metaPushHandler(payload, remoteRef.get(), ScopeImpl.this::onServMeta);
        }

        public void removeRegistry() {
            remotes.remove(remoteRef.get().getIdx());
            remoteServices.forEach((k, v) -> v.remove(remoteRef.get()));
        }

        @Override
        public void dispose() {
            info("{} is dispose", this);
            removeRegistry();
        }

        @Override
        public @NotNull Mono<Void> onClose() {
            info("{} is close", this);
            removeRegistry();
            return Mono.empty();
        }

        @Override
        public String toString() {
            return "ServiceRSocket{" +
                "remote=" + remoteRef.get() +
                ", serverName='" + server + '\'' +
                ", server=" + serverMode +
                '}';
        }
    }

}
