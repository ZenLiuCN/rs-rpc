package cn.zenliu.java.rs.rpc.core.impl;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.Server;
import cn.zenliu.java.rs.rpc.core.proto.Proto;
import cn.zenliu.java.rs.rpc.core.util.PayloadUtil;
import cn.zenliu.java.rs.rpc.core.util.ProxyUtil.ClientCreator;
import cn.zenliu.java.rs.rpc.core.util.ProxyUtil.ServiceRegister;
import cn.zenliu.java.rs.rpc.core.util.RSocketUtil;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Closeable;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.io.Serializable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

import static cn.zenliu.java.rs.rpc.core.util.ProxyUtil.clientCreatorBuilder;
import static cn.zenliu.java.rs.rpc.core.util.ProxyUtil.serviceRegisterBuilder;


/**
 * Scope implement
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */


public final class ScopeImpl extends ScopeContextImpl implements Scope, Serializable {
    private static final long serialVersionUID = -7734615300814212060L;

    transient final ClientCreator clientCreator = clientCreatorBuilder(this);
    transient final ServiceRegister serviceRegister = serviceRegisterBuilder(this);


    @Builder
    public ScopeImpl(String name, boolean route) {
        super(name == null || name.isEmpty() ? UUID.randomUUID().toString() : name, route);
    }

    static String dump(Remote remote, Payload payload) {
        return "\n REMOTE: " + remote.getName() + " | ROUTES:" + remote.getRoutes() + "| WEIGHT:" + remote.getWeight() + "\n" +
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
                    final RemoteImpl remoteImpl = RemoteImpl.builder()
                        .index(-getRemoteNames().size())
                        .rSocket(sending)
                        .build();
                    if (debug.get()) {
                        log.debug("remote connect to client [{}]", name);
                    }
                    final ServiceRSocket rSocket = new ServiceRSocket(name, remoteImpl, false);
                    remoteImpl.setServer(rSocket);
                    processRemoteUpdate(remoteImpl, null, false);
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
            final RemoteImpl remoteImpl = RemoteImpl.builder()
                .index(-getRemoteNames().size())
                .name("UNK")
                .rSocket(sending)
                .resumeEnabled(resume)
                .build();
            log.debug("remote connect to server [{}]", name);
            final ServiceRSocket rSocket = new ServiceRSocket(name, remoteImpl, true);
            remoteImpl.setServer(rSocket);
            processRemoteUpdate(remoteImpl, null, false);
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


    public final class ServiceRSocket implements Server, RSocket {
        @Getter final AtomicReference<Remote> ref = new AtomicReference<>();
        @Getter final String name;
        @Getter final boolean client;

        @Override
        public Server setRemote(Remote remote) {
            ref.set(remote);
            return this;
        }

        @Override
        public Remote getRemote() {
            return ref.get();
        }

        ServiceRSocket(String name, Remote remote, boolean client) {
            this.name = name;
            this.client = client;
            this.ref.set(remote);
        }

        @Override
        public @NotNull Mono<Void> fireAndForget(@NotNull Payload payload) {
            onDebug("{} on FireAndForget {} ", name, ScopeImpl.dump(ref.get(), payload));
            return PayloadUtil.fnfHandler(payload, ref.get(), ScopeImpl.this::onServMeta, ScopeImpl.this::onFNF);
        }

        @Override
        public @NotNull Mono<Payload> requestResponse(@NotNull Payload payload) {
            onDebug("{} on RequestResponse {}", name, ScopeImpl.dump(ref.get(), payload));
            return PayloadUtil.rrHandler(payload, ref.get(), ScopeImpl.this::onRR);
        }

        @Override
        public @NotNull Flux<Payload> requestStream(@NotNull Payload payload) {
            onDebug("{} on RequestStream {}", name, ScopeImpl.dump(ref.get(), payload));
            return PayloadUtil.rsHandler(payload, ref.get(), ScopeImpl.this::onRS);
        }

        @Override
        public @NotNull Mono<Void> metadataPush(@NotNull Payload payload) {
            onDebug("{} on MetadataPush {}", name, ScopeImpl.dump(ref.get(), payload));
            return PayloadUtil.metaPushHandler(payload, ref.get(), ScopeImpl.this::onServMeta);
        }

        public void removeRegistry() {
            remotes.remove(ref.get().getIndex());
            remoteServices.forEach((k, v) -> v.remove(ref.get()));
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
            return "Server{" +
                "remote=" + ref.get() +
                ", name='" + name + '\'' +
                ", isClient=" + client +
                '}';
        }

        @Override
        public String dump() {
            return toString();
        }
    }

}
