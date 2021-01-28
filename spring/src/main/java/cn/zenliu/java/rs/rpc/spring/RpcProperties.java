package cn.zenliu.java.rs.rpc.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "rpc.rs")
public class RpcProperties {
    /**
     * whether to create a new Scope (false: just use default scope)
     */
    boolean newScope = false;
    /**
     * whether to use routeing if this is a new Scope
     */
    boolean routeing = true;
    /**
     * Server binding address (Optional) or client connect address
     */
    String host;
    /**
     * Server|Client name
     */
    String name = "RSocket";
    /**
     * port of serve or port to connect
     */
    int port = 65530;
    /**
     * use Mimic delegate
     */
    boolean delegate = false;
    /**
     * use Mimic fluent mode
     */
    boolean fluent = false;
    /**
     * client mode or server mode
     */
    boolean client = false;
    /**
     * enable debug (enable debug is also enable trace)
     */
    boolean debug = false;
    /**
     * enable trace
     */
    boolean trace = false;
}
