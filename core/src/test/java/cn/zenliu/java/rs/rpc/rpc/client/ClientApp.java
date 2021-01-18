package cn.zenliu.java.rs.rpc.rpc.client;


import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import io.rsocket.transport.netty.client.TcpClientTransport;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ClientApp {
    public static void run() throws InterruptedException {
        final Scope service = Rpc.Global;
        service.setDebug(true);
        service.startClient("aClient", TcpClientTransport.create("127.0.0.1", 7000));
        final TestService bean = service.createClientService(TestService.class, null);
        Thread.sleep(5000);
        log.warn("call {}", bean.getInt());
        log.warn("call {}", bean.getResult(1L));
        log.warn("call {}", bean.getResult(-1L));
        Runtime.getRuntime().addShutdownHook(new Thread(service::release));
    }
}
