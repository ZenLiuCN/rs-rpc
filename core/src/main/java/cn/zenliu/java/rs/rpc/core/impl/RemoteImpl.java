package cn.zenliu.java.rs.rpc.core.impl;

import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.RouteMeta;
import cn.zenliu.java.rs.rpc.core.element.Server;
import cn.zenliu.java.rs.rpc.core.util.PayloadUtil;
import io.rsocket.RSocket;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
public final class RemoteImpl implements Remote {
    private static final long serialVersionUID = -7451694137919068872L;
    @Builder.Default final String name = UNKNOWN_NAME;
    @Builder.Default final List<String> routes = new ArrayList<>();
    @Builder.Default final boolean resumeEnabled = false;
    transient RSocket rSocket;
    transient Server server;
    @Builder.Default transient int weight = 1;
    @Builder.Default transient int index = -1;

    @Override
    public Remote setRoutes(List<String> routes) {
        this.routes.clear();
        this.routes.addAll(routes);
        return this;
    }

    public Remote setServer(@NotNull Server server) {
        this.server = server;
        server.setRemote(this);
        if (rSocket != null) rSocket.onClose().doOnTerminate(server::removeRegistry).subscribe();
        return this;
    }

    public Remote setRSocket(RSocket socket) {
        this.rSocket = socket;
        return this;
    }

    public Remote setWeight(int weight) {
        this.weight = weight;
        return this;
    }

    public Remote setIndex(int idx) {
        this.index = idx;
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
        return routes.contains(root);
    }

    public Remote updateRouteMeta(RouteMeta meta) {
        //always a new one for match remove logic
        return fromMeta(meta)
            .setServer(server)
            .setRSocket(rSocket)
            .setIndex(index)
            .setWeight(1);
    }

    public Remote plusWeight() {
        weight++;
        return this;
    }

    public Remote minusWeight() {
        weight--;
        return this;
    }

    @Override
    public String toString() {
        return "REMOTE@" + name +
            "{=>:" + routes +
            "," + rSocket +
            ",resume:" + resumeEnabled +
            ",server:{'" + server.getName() + '\'' +
            ", " + (server.isClient() ? "server" : "client") +
            '}' +
            ",w:" + weight +
            ",i:" + getIndex() +
            "}";
    }

    @Override
    public String dump() {
        return toString();
    }

    public void pushRouteMeta(RouteMeta meta) {
        if (!resumeEnabled) {
            log.debug("REMOTE {} will push Serv meta {} \n via MetadataPush to {}", server.getName(), meta, name);
            rSocket.metadataPush(PayloadUtil.buildRouteMeta(meta)).subscribe();
        } else {
            log.debug("REMOTE {} will push Serv meta {} \n via FNF to {}", server.getName(), meta, name);
            rSocket.fireAndForget(PayloadUtil.buildRouteMeta(meta)).subscribe();
        }
    }

    public static Remote fromMeta(RouteMeta r) {
        return RemoteImpl.builder()
            .name(r.getName())
            .resumeEnabled(r.isResumeEnabled())
            .routes(r.getRoutes() == null || r.getRoutes().isEmpty() ?
                Collections.emptyList() : new ArrayList<>(r.getRoutes()))
            .build();

    }


}
