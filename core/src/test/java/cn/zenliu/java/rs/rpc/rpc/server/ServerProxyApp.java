package cn.zenliu.java.rs.rpc.rpc.server;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.ScopeImpl;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-15
 */

@Slf4j
public class ServerProxyApp {
    public static void run(boolean debug, boolean trace, boolean routeing, boolean resume, Duration timeout) {
        new Thread(() -> {
            val service = Rpc.fetchOrCreate("ProxyServer", routeing);
            service.setDebug(debug);
            service.setTrace(trace);
            service.setTimeout(timeout);

            final Config.ServerConfig.Server.ServerBuilder builder = Config.Server.builder().port(7000);
            if (resume) builder.resume(Config.Resume.builder()
                .sessionDuration(Duration.ofMinutes(15))
                .retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                .cleanupStoreOnKeepAlive(true)
                .streamTimeout(Duration.ofSeconds(5))
                .build());

            service.startServer("aServer", builder.build());
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                final ScopeImpl scope = (ScopeImpl) service;
                log.warn("service {} info {} ", service.getName(), scope.getRoutes().get());
                service.release();
            }));
        }).start();
    }
}
