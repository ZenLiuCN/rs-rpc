package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
public class ClientApp {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        run();
    }

    public static void run() throws InterruptedException, ExecutionException {
        final Scope service = Rpc.fetchOrCreate("FinalClient", true);
        final ExecutorService executorService = Executors.newCachedThreadPool();
        service.setDebug(false);
        service.setTrace(true);
        service.startClient("aClient", Config.Client.builder()
            .host("localhost").port(7000)
            .resume(Config.Resume.builder()
                .sessionDuration(Duration.ofMinutes(15))
                //.retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                .build())
            .build());
        final TestService bean = service.createClientService(TestService.class, null);
        Thread.sleep(500);//wait for sync
        log.error("call getInt {}", bean.getInt());
        log.warn("call getResult {}", bean.getResult(1L));
        log.warn("call getResult {}", bean.getResult(-1L));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            service.release();
            executorService.shutdown();
        }));
    }
}
