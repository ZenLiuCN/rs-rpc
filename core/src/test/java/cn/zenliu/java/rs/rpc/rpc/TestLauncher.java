package cn.zenliu.java.rs.rpc.rpc;

import cn.zenliu.java.rs.rpc.rpc.client.ClientApp;
import cn.zenliu.java.rs.rpc.rpc.client.ClientProxyApp;
import cn.zenliu.java.rs.rpc.rpc.server.ServerProxyApp;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.concurrent.ExecutionException;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-16
 */
@Slf4j
public class TestLauncher {
    public static final boolean debug = true;
    public static final boolean trace = false;
    public static final boolean resume = false;
    public static final boolean routeing = true;
    public static final Duration timeout = Duration.ofMillis(500);

    public static void main(String[] args) throws InterruptedException, ExecutionException {
        launcherProxy();
    }

    static void launcherProxy() throws InterruptedException, ExecutionException {
        ServerProxyApp.run(debug, trace, routeing, resume, timeout);
        ClientProxyApp.run(debug, trace, routeing, resume, timeout);
        Thread.sleep(2000);
        ClientApp.run(debug, trace, routeing, resume, timeout);
    }

/*    static void launcherNormal() throws InterruptedException, ExecutionException {
        ServerApp.run();
        ClientApp.run();
    }*/
}
