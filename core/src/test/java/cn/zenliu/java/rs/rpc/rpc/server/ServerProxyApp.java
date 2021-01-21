package cn.zenliu.java.rs.rpc.rpc.server;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.core.Rpc;
import lombok.val;

import java.time.Duration;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-15
 */


public class ServerProxyApp {

    public static void run() {
        new Thread(() -> {
            val service = Rpc.fetchOrCreate("ProxyServer", true);
            service.setDebug(false);
            service.setTrace(true);
            service.startServer("aServer", Config.Server.builder().port(7000)
                .resume(Config.Resume.builder()
                    .sessionDuration(Duration.ofMinutes(15))
                    .retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                    .cleanupStoreOnKeepAlive(true)
                    .streamTimeout(Duration.ofSeconds(5))
                    .build())
                .build());
            Runtime.getRuntime().addShutdownHook(new Thread(service::release));
        }).start();
    }
}
