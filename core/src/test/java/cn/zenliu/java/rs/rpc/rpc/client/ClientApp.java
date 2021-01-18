package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApp {
    public static void run() throws InterruptedException {
        final Scope service = Rpc.Global;
        service.setDebug(true);
        service.startClient("aClient", Config.Client.builder().host("localhost").port(7000).build());
        final TestService bean = service.createClientService(TestService.class, null);
        Thread.sleep(5000);
        log.warn("call {}", bean.getInt());
        log.warn("call {}", bean.getResult(1L));
        log.warn("call {}", bean.getResult(-1L));
        Runtime.getRuntime().addShutdownHook(new Thread(service::release));
    }
}
