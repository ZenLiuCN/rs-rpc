package cn.zenliu.java.rs.rpc.rpc.server;

import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import cn.zenliu.java.rs.rpc.rpc.common.TestServiceImpl;
import io.rsocket.transport.netty.server.TcpServerTransport;
import lombok.val;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-15
 */


public class ServerApp {
    public static void run() {
        new Thread(() -> {
            val rpcService = Rpc.newInstance("server", false);
            rpcService.setDebug(true);
            rpcService.registerService(new TestServiceImpl(), TestService.class, null);
            rpcService.startServer("aServer", TcpServerTransport.create(7000));
            Runtime.getRuntime().addShutdownHook(new Thread(rpcService::release));
        }).start();
    }
}
