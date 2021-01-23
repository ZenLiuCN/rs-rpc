package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.TestLauncher;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import cn.zenliu.java.rs.rpc.rpc.common.TestServiceImpl;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

import static cn.zenliu.java.rs.rpc.rpc.Util.registerShutdown;

@Slf4j
public class ClientProxyApp {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        run(TestLauncher.debug, TestLauncher.trace, TestLauncher.routeing, TestLauncher.resume, TestLauncher.timeout);
    }

    public static void run(boolean debug, boolean trace, boolean routeing, boolean resume, Duration timeout) throws InterruptedException, ExecutionException {
        new Thread(() -> {
            final Scope service = Rpc.newScope("aProxy", routeing);
            service.setDebug(debug);
            service.setTrace(trace);
            service.setTimeout(timeout);
            service.registerService(new TestServiceImpl(), TestService.class, null);
            final Config.Client.ClientBuilder config = Config.Client.builder()
                .host("localhost").port(7000);
            if (resume) config.resume(Config.Resume.builder()
                .sessionDuration(Duration.ofMinutes(15))
                //.retry(Config.Retry.FixedDelay.of(100, Duration.ofDays(10)))
                .build());
            service.startClient("aProxyClient", config.build());
            registerShutdown(service, log);
        }).start();

    }
}
