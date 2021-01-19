package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ClientApp {
    public static void run() throws InterruptedException, ExecutionException {
        final Scope service = Rpc.Global;
        final ExecutorService executorService = Executors.newCachedThreadPool();
        service.setDebug(true);
        final TestService bean = executorService.submit(() -> {
            service.startClient("aClient", Config.Client.builder()
                .host("localhost").port(7000)
                /* .resume(Config.Resume.builder()
                     .sessionDuration(Duration.ofMinutes(15))
                     //.retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                     .build())*/
                .build());
            return service.createClientService(TestService.class, null);
        }).get();
        Thread.sleep(2000);
        log.warn("call {}", bean.getInt());
        log.warn("call {}", bean.getResult(1L));
        log.warn("call {}", bean.getResult(-1L));
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.release();
            executorService.shutdown();
        }));
    }
}
