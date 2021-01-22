package cn.zenliu.java.rs.rpc.core;


import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import org.jetbrains.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static cn.zenliu.java.rs.rpc.core.Remote.NONE_META_NAME;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface ContextRoutes extends ContextRemoteServices, ContextServices {
    static char ROUTE_MARK = '?';

    AtomicReference<Set<String>> getRoutes();

    default void updateRoutes() {
        getRoutes().set(getServices().toSetWith(isRoute() ? getDomains() : null));
    }

    default Payload buildServMeta(@Nullable Remote forRemote) {
        final ServMeta.ServMetaBuilder builder = ServMeta.builder()
            .name(getName())
            .service(new ArrayList<>(getRoutes().get()));
        if (forRemote != null) builder.known(forRemote.service);
        return DefaultPayload.create(DefaultPayload.EMPTY_BUFFER, ByteBuffer.wrap(Proto.to(builder.build())));
    }

    default void processRemoteUpdate(Remote in, Remote old, boolean known) {
        if (known) {
            onDebug(log -> log.debug("[{}] remove and update meta \n FROM {} \nTO {} with sync ", getName(), old, in));
            pushServMeta(null, in);
        }
        if (old == null && in.getName().equals(NONE_META_NAME)) {
            onDebug(log -> log.debug("[{}] a new connection found {} \n sync serv meta :{}", getName(), in, getRoutes().get()));
            getRemotes().put(in.idx, in);
            if (!known) pushServMeta(null, in);//todo
            return;
        }

        final int remoteIdx;
        if (old != null && old.getName().equals(NONE_META_NAME)) {
            remoteIdx = prepareRemoteName(in.getName());
            getRemotes().remove(old.getIdx());
            pushServMeta(null, in);//push current meta to this remote for first update real Meta
        } else {
            remoteIdx = old != null && old.getIdx() >= 0 ? old.getIdx() : prepareRemoteName(in.getName());
        }

        if (getRemotes().containsKey(remoteIdx)) {
            in.setIdx(remoteIdx);
            final Remote olderRemote = getRemotes().get(remoteIdx);
            in.setWeight(Math.max(olderRemote.weight, 1));
            onDebug(log -> log.debug("[{}] update meta for remote {}[{}] ", getName(), in, remoteIdx));
            getRemotes().put(remoteIdx, in);
            onDebug(log -> log.debug("[{}] remove and update meta \n FROM {} \nTO {}", getName(), old, in));
        } else {
            onDebug(log -> log.debug("[{}] new meta from remote {}[{}] ", getName(), in, remoteIdx));
            if (in.idx == -1) in.setIdx(remoteIdx);
            if (in.weight == 0) in.setWeight(1);
            getRemotes().put(remoteIdx, in);
        }
        if (updateRemoteService(in, old)) {
            updateRoutes();
            syncServMeta(in);
        }
    }

    /**
     * Sync Meta data: push meta to all remotes
     */
    default void syncServMeta(Remote... exclude) {
        if (getRemotes().isEmpty()) return;
        onDebug(log -> log.debug("[{}] will sync serv meta {} to remote {}  ", getName(), getRoutes().get(), getRemotes()));
        getRemotes().forEach((i, v) -> {
            for (Remote remote : exclude) {
                if (remote == v) {
                    onDebug(log -> log.debug("[{}] skip remote {} for is marked as Skip", getName(), getRemoteNames().get(i)));
                    return;
                }
            }
            onDebug(log -> log.debug(" [{}] sync serv meta to remote [{}]: {} ", getName(), v, getRoutes().get()));
            pushServMeta(null, v);
        });
    }

    default void pushServMeta(@Nullable Payload servMeta, Remote target) {
        target.pushServMeta(servMeta == null ? buildServMeta(target) : servMeta);
    }

}

