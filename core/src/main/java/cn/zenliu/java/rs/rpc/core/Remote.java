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
    @Builder.Default final Set<String> service = new HashSet<>();
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
    @Builder.Default transient int weight = 1;
    /**
     * remote index for faster location
     */
    @Builder.Default transient int idx = -1;

    public static Remote fromMeta(ServMeta r, boolean resume) {
        return Remote.builder()
            .name(r.name)
            .resume(resume)
            .service(r.service == null || r.service.isEmpty() ? Collections.emptySet() : new HashSet<>(r.service))
            .build();

    }

    public static Map<String, Object> dumpRemote(Remote x) {
        Map<String, Object> map = new HashMap<>();
        map.put("name", x.getName());
        map.put("server", x.server.server);
        map.put("service", x.service);
        map.put("weight", x.getWeight());
        map.put("disposed", x.socket.isDisposed());
        return map;

    }

    public Remote setServer(@NotNull ServiceRSocket server) {
        this.server = server;
        server.remoteRef.set(this);
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
        //always a new one for match remove logic
        return fromMeta(meta, resume)
            .setServer(server)
            .setSocket(socket)
            .setIdx(idx)
            .setWeight(1);
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
        return "REMOTE@" + name +
            "{=>:" + service +
            "," + socket +
            ",resume:" + resume +
            ",server:{'" + server.server + '\'' +
            ", " + (server.serverMode ? "server" : "client") +
            '}' +
            ",w:" + weight +
            ",i:" + idx +
            "}";
    }


    public void pushServMeta(Payload meta) {
        if (!resume) {
            log.debug("REMOTE {} will push Serv meta {} \n via MetadataPush to {}", server.server, dumpMeta(meta), name);
            socket.metadataPush(meta).subscribe();
        } else {
            log.debug("REMOTE {} will push Serv meta {} \n via FNF to {}", server.server, dumpMeta(meta), name);
            socket.fireAndForget(meta).subscribe();
        }
    }

    private String dumpMeta(Payload meta) {
        return ByteBufUtil.prettyHexDump(meta.sliceMetadata());
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
