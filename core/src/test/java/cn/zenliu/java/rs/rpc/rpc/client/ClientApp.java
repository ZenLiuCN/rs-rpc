package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.TestLauncher;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static cn.zenliu.java.rs.rpc.rpc.Util.registerShutdown;

@Slf4j
public class ClientApp {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        run(TestLauncher.debug, TestLauncher.trace, TestLauncher.routeing, TestLauncher.resume, TestLauncher.timeout);
    }

    public static void run(boolean debug, boolean trace, boolean routeing, boolean resume, Duration timeout) throws InterruptedException, ExecutionException {
        final Scope service = Rpc.fetchOrCreate("FinalClient", routeing);
        service.setDebug(debug);
        service.setTrace(trace);
        service.setTimeout(timeout);
        final Config.Client.ClientBuilder config = Config.Client.builder()
            .host("localhost").port(7000);
        if (resume) config.resume(Config.Resume.builder()
            .sessionDuration(Duration.ofMinutes(15))
            //.retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
            .build());
        service.startClient("aClient", config.build());
        final TestService bean = service.createClientService(TestService.class, null, true);
        Thread.sleep(500);//wait for sync
        log.error("call getInt {}", bean.getInt());
        log.error("call getResult {}", bean.getResult(1L));
        bean.ffi(-1);
        log.error("call getResult {}", bean.getResult(-1L));
        registerShutdown(service, log);
    }
}
