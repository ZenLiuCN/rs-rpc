package cn.zenliu.java.rs.rpc.core.element;

import io.rsocket.RSocket;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
public interface Remote extends Serializable {
    Comparator<Remote> weightComparator = Comparator.comparingInt(Remote::getWeight);
    String UNKNOWN_NAME = "UNK";

    /**
     * the scope name
     */
    @NotNull String getName();

    /**
     * the scope enabled resume
     */
    boolean isResumeEnabled();

    /**
     * current known scope routes
     */
    List<String> getRoutes();

    /**
     * update routes
     */
    Remote setRoutes(List<String> routes);

    /**
     * weight in current scope
     */
    int getWeight();

    Remote setWeight(int weight);

    Remote plusWeight();

    Remote minusWeight();

    int getIndex();

    Remote setIndex(int index);

    Server getServer();

    Remote setServer(Server server);

    RSocket getRSocket();

    Remote setRSocket(RSocket rSocket);

    String dump();

    Remote updateRouteMeta(RouteMeta meta);

    void pushRouteMeta(RouteMeta meta);

    static Map<String, Object> dumpRemote(Remote x) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", x.getName());
        map.put("server", x.getServer().getName());
        map.put("routes", x.getRoutes());
        map.put("weight", x.getWeight());
        map.put("disposed", x.getRSocket().isDisposed());
        return map;
    }
}
