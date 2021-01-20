package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import cn.zenliu.java.rs.rpc.rpc.common.TestServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

@Slf4j
public class ClientProxyApp {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        run();
    }

    public static void run() throws InterruptedException, ExecutionException {
        new Thread(() -> {
            final Scope service = Rpc.newScope("aProxy", true);
            service.setDebug(true);
            service.startClient("aProxyClient", Config.Client.builder()
                .host("localhost").port(7000)
                .resume(Config.Resume.builder()
                    .sessionDuration(Duration.ofMinutes(15))
                    //.retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                    .build())
                .build());
            service.registerService(new TestServiceImpl(), TestService.class, null);
            Runtime.getRuntime().addShutdownHook(new Thread(service::release));
        }).start();

    }
}
