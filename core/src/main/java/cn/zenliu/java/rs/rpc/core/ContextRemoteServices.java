package cn.zenliu.java.rs.rpc.core;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import static cn.zenliu.java.rs.rpc.core.ScopeContextImpl.ROUTE_MARK;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-23
 */
interface ContextRemoteServices extends ContextRemotes {
    Map<Integer, ConcurrentSkipListSet<Remote>> getRemoteServices();

    UniqueList getDomains();

    default int getOrAddDomain(String domain) {
        return getDomains().getOrAdd(domain);
    }

    default @Nullable Remote findRemoteService(String domain) {
        onDebug(log -> log.debug("[{}] before findRouteDomain {} in {}", getName(), domain, getDomains()));
        int index = getDomains().indexOf(domain);
        if (index == -1 && !isRoute()) {
            onDebug(log -> log.debug("[{}] find no domain {} in {} with no routeing enabled.", getName(), domain, getDomains()));
            return null;
        } else if (index == -1) {
            onDebug(log -> log.debug("[{}] try find domain {} in {} with routeing", getName(), domain + ROUTE_MARK, getDomains()));
            index = getDomains().indexOf(domain + ROUTE_MARK);
        }
        if (index == -1) {
            onDebug(log -> log.debug("[{}] find no domain {} in {} with routeing", getName(), domain + ROUTE_MARK, getDomains()));
            return null;
        }
        final ConcurrentSkipListSet<Remote> remotes = getRemoteServices().get(index);
        if (remotes.isEmpty()) {
            onDebug(log -> log.debug("[{}] find domain {} in {} with routeing, but found no Remote exists in {}!", getName(), domain + ROUTE_MARK, getDomains(), getRemoteServices()));
            return null;
        }
        return remotes.first();
    }

    default boolean updateRemoteService(Remote newRemote, Remote oldRemote) {
        boolean updated = false;
        onDebug(log -> log.debug("[{}] before update remote service: {} to {} \n {} {}", getName(), newRemote, oldRemote, getDomains(), getRemoteServices()));
        if (oldRemote != null) {
            oldRemote.service.forEach(v -> removeOldRemoteService(v, oldRemote));
        }
        onDebug(log -> log.debug("[{}] after remove old when update remote service: {} to {} \n {} {}", getName(), newRemote, oldRemote, getDomains(), getRemoteServices()));
        for (String domain : newRemote.service) {
            onDebug(log -> log.debug("[{}] before register remote {} with service {}", getName(), newRemote.name, domain));
            final int index = getOrAddDomain(domain);
            updated = updated || index == getDomains().size();
            ConcurrentSkipListSet<Remote> remotes = getRemoteServices().get(index);
            if (remotes == null) {
                remotes = new ConcurrentSkipListSet<>(Remote.weightComparator);
                getRemoteServices().put(index, remotes);
            }
            remotes.add(newRemote);
        }
        onDebug(log -> log.debug("[{}] after update remote service: {} to {} \n {} {}", getName(), newRemote, oldRemote, getDomains(), getRemoteServices()));
        return updated;
    }

    default void removeOldRemoteService(String domain, Remote oldRemote) {
        int index = getDomains().indexOf(domain);
        if (index == -1 && !isRoute()) return;
        getRemoteServices().get(index).remove(oldRemote);
        if (isRoute()) index = getDomains().indexOf(domain + ROUTE_MARK);
        if (index == -1) return;
        getRemoteServices().get(index).remove(oldRemote);
    }

}


