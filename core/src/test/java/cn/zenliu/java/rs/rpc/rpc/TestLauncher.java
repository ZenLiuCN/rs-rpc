package cn.zenliu.java.rs.rpc.rpc;

import cn.zenliu.java.rs.rpc.rpc.client.ClientApp;
import cn.zenliu.java.rs.rpc.rpc.client.ClientProxyApp;
import cn.zenliu.java.rs.rpc.rpc.server.ServerProxyApp;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutionException;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */
@Slf4j
public class TestLauncher {
    public static void main(String[] args) throws InterruptedException, ExecutionException {
        launcherProxy();
    }

    static void launcherProxy() throws InterruptedException, ExecutionException {
        ServerProxyApp.run();
        ClientProxyApp.run();
        ClientApp.run();
    }

/*    static void launcherNormal() throws InterruptedException, ExecutionException {
        ServerApp.run();
        ClientApp.run();
    }*/
}
