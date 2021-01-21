package cn.zenliu.java.rs.rpc.core;

import io.rsocket.Payload;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static cn.zenliu.java.rs.rpc.core.Remote.NONE_META_NAME;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-21
 */
public interface RouteingContext extends ScopeContext {
    Payload getServMeta(Remote remote);


    /**
     * process ServiceMeta Request
     *
     * @param meta   payload
     * @param remote current Meta
     */
    default void servMetaProcess(ServMeta meta, Remote remote) {
        final Remote newRemote = remote.updateFromMeta(meta);
        //noinspection ConstantConditions
        addOrUpdateRemote(newRemote, remote,
            // if known==null => routes should empty
            !(meta.known == null ? getRoutes().get().isEmpty() : meta.known.containsAll(getRoutes().get()))
        );
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
            pushMeta(null, v);
        });
    }


    /**
     * add or update Meta
     *
     * @param remote    the remote meta
     * @param oldRemote the old meta
     */
    default void addOrUpdateRemote(Remote remote, Remote oldRemote, boolean sync) {
        //noinspection ConstantConditions
        if (oldRemote == null && remote.name.equals(NONE_META_NAME)) {
            onDebug(log -> log.debug("[{}] a new connection found {} \n sync serv meta :{}", getName(), remote, getRoutes().get()));
            getRemotes().put(remote.idx, remote);
            pushMeta(null, remote);
            return;
        }
        final int remoteIdx;
        if (oldRemote.getName().equals(NONE_META_NAME)) {
            remoteIdx = prepareRemoteName(remote.getName());
            getRemotes().remove(oldRemote.idx);
            pushMeta(null, remote);//push current meta to this remote for first update real Meta
        } else {
            remoteIdx = oldRemote.idx >= 0 ? oldRemote.idx : prepareRemoteName(remote.name);
        }
        if (sync) {
            onDebug(log -> log.debug("[{}] remove and update meta \n FROM {} \nTO {} with sync ", getName(), oldRemote, remote));
            pushMeta(null, remote);
        }
        if (getRemotes().containsKey(remoteIdx)) {
            remote.setIdx(remoteIdx);
            final Remote olderRemote = getRemotes().get(remoteIdx);
            remote.setWeight(Math.max(olderRemote.weight, 1));
            onDebug(log -> log.debug("[{}] update meta for remote {}[{}] ", getName(), remote, remoteIdx));
            getRemotes().put(remoteIdx, remote);
            onDebug(log -> log.debug("[{}] remove and update meta \n FROM {} \nTO {}", getName(), oldRemote, remote));
        } else {
            onDebug(log -> log.debug("[{}] new meta from remote {}[{}] ", getName(), remote, remoteIdx));
            if (remote.idx == -1) remote.setIdx(remoteIdx);
            if (remote.weight == 0) remote.setWeight(1);
            getRemotes().put(remoteIdx, remote);
        }
        final Set<String> routes = getRoutes().get();
        updateRemoteService(remote, oldRemote);
        final Set<String> newRoutes = getRoutes().get();
        if (!(newRoutes.size() == routes.size() && newRoutes.containsAll(routes))) {
            //onDebugElse(null,log->log.warn("[{}] update info from {} to {}",getName(),routes,newRoutes));
            syncServMeta(remote);
        }
    }

    /**
     * current is not support metadata push with Resume enabled!
     * <b>note:</b>
     * Should METADATA_PUSH should be part of resumption? #235
     */
    default void pushMeta(@Nullable Payload meta, Remote remote) {
        remote.pushMeta(meta == null ? getServMeta(remote) : meta);
    }
}
