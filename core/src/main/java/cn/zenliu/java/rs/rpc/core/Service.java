package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.core.ScopeImpl.ServiceRSocket;
import io.rsocket.RSocket;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Service Meta is Presence a Connection between Remote and Local.
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-17
 */
@Builder
@Getter
@Setter
public final class Service implements Serializable {
    static final String NONE_META_NAME = "UNK";
    private static final long serialVersionUID = -7451694137919068872L;
    public static Comparator<Service> weightComparator = Comparator.comparingInt(Service::getWeight);
    /**
     * Remote Name
     */
    @Builder.Default final String name = NONE_META_NAME;
    @Builder.Default final Set<String> service = new CopyOnWriteArraySet<>();
    /**
     * Remote RSocket
     */
    transient RSocket socket;
    /**
     * local RSocket
     */
    transient ServiceRSocket server;
    /**
     * weight to do load balance or something else
     */
    transient int weight = 0;
    /**
     * remote index for faster location
     */
    transient int idx = -1;

    public static Service fromMeta(ServMeta r) {
        return Service.builder()
            .name(r.name)
            .service(r.service == null ? Collections.emptySet() : new HashSet<>(r.service))
            .build();

    }

    public static Map<String, Object> dumpMeta(Service x) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", x.getName());
        map.put("localServerName", x.server.serverName);
        map.put("service", x.service);
        map.put("weight", x.getWeight());
        map.put("disposed", x.socket.isDisposed());
        return map;

    }

    public Service setServer(@NotNull ServiceRSocket server) {
        this.server = server;
        server.metaRef.set(this);
        if (socket != null) socket.onClose().doOnTerminate(server::removeRegistry).subscribe();
        return this;
    }

    public Service setSocket(RSocket socket) {
        this.socket = socket;
        return this;
    }

    public Service setWeight(int weight) {
        this.weight = weight;
        return this;
    }

    public Service setIdx(int idx) {
        this.idx = idx;
        return this;
    }

    /**
     * check current Meta is Accept a Call Domain
     *
     * @param domain target call domain
     * @return true
     */
    public boolean accept(String domain) {
        final String root = domain.substring(0, domain.indexOf("#"));
        return service.contains(root);
    }

    public Service updateFromMeta(ServMeta meta) {
        if (!this.name.equals(meta.name)) {
            return fromMeta(meta).setServer(server).setSocket(socket).setIdx(idx).setWeight(0);
        }
        synchronized (this) {
            service.clear();
            service.addAll(meta.service);
        }
        return this;
    }

    public Service higher() {
        weight++;
        return this;
    }

    public Service lower() {
        weight--;
        return this;
    }

    @Override
    public String toString() {
        return "\n--------------META-----------------" +
            "\n name='" + name + '\'' +
            "\n service=" + service +
            "\n socket=" + socket +
            "\n server=" + server +
            "\n weight=" + weight +
            "\n idx=" + idx +
            "\n-----------------------------------";
    }
}
