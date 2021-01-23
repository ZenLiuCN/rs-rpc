package cn.zenliu.java.rs.rpc.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Scope is a zone of rpc servers and clients
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-15
 */
public interface Scope {
    /**
     * scope name(which is auto uniquify to a {@link JvmUnique#uniqueNameWithRandom(String)}
     */
    String getName();

    boolean isRoute();

    /**
     * set timeout of call
     *
     * @param timeout maximum call waiting
     */
    void setTimeout(@Nullable Duration timeout);

    /**
     * change debug mode
     *
     * @param debug whether open debug mode
     */
    void setDebug(boolean debug);

    void setTrace(boolean trace);

    /**
     * create a new RSocket Server
     *
     * @param name   simple server name
     * @param config server configuration
     */
    void startServer(String name, Config.ServerConfig config);

    /**
     * create a new RSocket Client
     *
     * @param name   simple client name
     * @param config client configuration
     */
    void startClient(String name, Config.ClientConfig config);

    /**
     * release and close all cache ,include all RS server and client
     */
    void release();

    /**
     * build a Proxy Rpc Service
     *
     * @param clientKlass       service ( must be a interface)
     * @param argumentProcessor parameter and result processor for each method (<b>if there is some interface parameter or result or else with nested interface that Implement may not exists in remote service</b>)
     * @param useFNF            use fireAndForgot for void return method
     * @param <T>               service type
     * @return the Instance
     */
    <T> T createClientService(Class<T> clientKlass, @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor, boolean useFNF);

    /**
     * register a local service to serve
     *
     * @param service         the service instance
     * @param serviceKlass    the define interface of the service
     * @param resultProcessor parameter and result processor for each method (<b>if there is some interface parameter or result or else with nested interface that Implement may not exists in remote service</b>)
     * @param <T>             type
     */
    <T> void registerService(
        T service,
        Class<T> serviceKlass,
        @Nullable Map<String, Function<Object, Object>> resultProcessor
    );

    /**
     * service list
     */
    @Unmodifiable List<String> names();

    /**
     * close a named Server
     *
     * @param name server name
     */
    boolean dispose(@NotNull String name);
}
