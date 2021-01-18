package cn.zenliu.java.rs.rpc.rpc.server;

import cn.zenliu.java.rs.rpc.api.Config;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.rpc.common.TestService;
import cn.zenliu.java.rs.rpc.rpc.common.TestServiceImpl;
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
            rpcService.startServer("aServer", Config.Server.builder().port(7000).build());
            Runtime.getRuntime().addShutdownHook(new Thread(rpcService::release));
        }).start();
    }
}
