package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.core.ScopeImpl.ServiceRSocket;
import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.RSocket;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
@EqualsAndHashCode
public final class Remote implements Serializable {
    static final String NONE_META_NAME = "UNK";
    private static final long serialVersionUID = -7451694137919068872L;
    public static Comparator<Remote> weightComparator = Comparator.comparingInt(Remote::getWeight);
    /**
     * Remote Name
     */
    @Builder.Default final String name = NONE_META_NAME;
    @Builder.Default final Set<String> service = new CopyOnWriteArraySet<>();
    @Builder.Default final boolean resume = false;
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

    public static Remote fromMeta(ServMeta r) {
        return Remote.builder()
            .name(r.name)
            .service(r.service == null ? Collections.emptySet() : new HashSet<>(r.service))
            .build();

    }

    public static Map<String, Object> dumpRemote(Remote x) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", x.getName());
        map.put("localServerName", x.server.server);
        map.put("service", x.service);
        map.put("weight", x.getWeight());
        map.put("disposed", x.socket.isDisposed());
        return map;

    }

    public Remote setServer(@NotNull ServiceRSocket server) {
        this.server = server;
        server.serviceRef.set(this);
        if (socket != null) socket.onClose().doOnTerminate(server::removeRegistry).subscribe();
        return this;
    }

    public Remote setSocket(RSocket socket) {
        this.socket = socket;
        return this;
    }

    public Remote setWeight(int weight) {
        this.weight = weight;
        return this;
    }

    public Remote setIdx(int idx) {
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

    public Remote updateFromMeta(ServMeta meta) {
        if (!this.name.equals(meta.name)) {
            return fromMeta(meta).setServer(server).setSocket(socket).setIdx(idx).setWeight(0);
        }
        synchronized (this) {
            service.clear();
            service.addAll(meta.service);
        }
        return this;
    }

    public Remote higher() {
        weight++;
        return this;
    }

    public Remote lower() {
        weight--;
        return this;
    }

    @Override
    public String toString() {
        return "\n--------------SERVICE-----------------" +
            "\n name='" + name + '\'' +
            "\n service=" + service +
            "\n socket=" + socket +
            "\n resume=" + resume +
            "\n server=" + "ServiceRSocket{" +
            "serverName='" + server.server + '\'' +
            ", isServer=" + server.serverMode +
            '}' +
            "\n weight=" + weight +
            "\n idx=" + idx +
            "\n-----------------------------------";
    }

    public void pushMeta(Payload meta) {
        if (!resume) {
            log.debug("service {} will push meta via MetadataPush", name);
            socket.metadataPush(meta).subscribe();
        } else {
            log.debug("service {} will push meta via FNF", name);
            socket.fireAndForget(meta).subscribe();
        }
    }

    public ServMeta tryHandleMeta(Payload meta) {
        //a client is never know if server support resume(also no need to )
        try {
            ServMeta me = Proto.from(ByteBufUtil.getBytes(meta.sliceMetadata()), ServMeta.class);
            meta.release();
            return me;
        } catch (Exception e) {
            log.debug("error to try decode ServMeta, is maybe just normal", e);
            return null;
        }
    }
}
