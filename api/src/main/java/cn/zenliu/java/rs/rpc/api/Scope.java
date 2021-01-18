package cn.zenliu.java.rs.rpc.api;

import io.rsocket.Closeable;
import io.rsocket.transport.ClientTransport;
import io.rsocket.transport.ServerTransport;
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

    /**
     * create a new RSocket Server
     *
     * @param name      simple server name
     * @param transport 串串
     */
    void startServer(String name, ServerTransport<? extends Closeable> transport);

    /**
     * create a new RSocket Client
     *
     * @param name      simple client name
     * @param transport 传输方法
     */
    void startClient(String name, ClientTransport transport);

    /**
     * 释放方法,关闭所有本地服务和远程链接
     */
    void release();

    /**
     * 构建RPC代理服务
     *
     * @param clientKlass       服务类型(接口)
     * @param argumentProcessor (方法名:转换方法)<b>采用的序列化方法不支持自动序列化处理接口,需要使用代理类或公共实现</b>
     * @param <T>               服务类型
     * @return 服务实例
     */
    <T> T createClientService(Class<T> clientKlass, @Nullable Map<String, Function<Object[], Object[]>> argumentProcessor);

    /**
     * 注册本地服务,提供RPC
     *
     * @param service         服务实例
     * @param serviceKlass    服务接口
     * @param resultProcessor 特殊结果处理器(方法名:转换方法)<b>采用的序列化方法不支持自动序列化处理接口,需要使用代理类或公共实现</b>
     * @param <T>             服务类型
     */
    <T> void registerService(
        T service,
        Class<T> serviceKlass,
        @Nullable Map<String, Function<Object, Object>> resultProcessor
    );

    /**
     * 服务清单
     */
    @Unmodifiable List<String> names();

    /**
     * 关闭指定服务
     *
     * @param name 服务名称
     */
    boolean dispose(@NotNull String name);
}
