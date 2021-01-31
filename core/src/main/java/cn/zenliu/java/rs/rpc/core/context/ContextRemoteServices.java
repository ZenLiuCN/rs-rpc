package cn.zenliu.java.rs.rpc.core.context;

import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.element.UniqueList;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentSkipListSet;

import static cn.zenliu.java.rs.rpc.core.context.ContextRoutes.ROUTE_MARK;

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
        onDebug("before findRouteDomain {} in {}", domain, getDomains());
        int index = getDomains().indexOf(domain);
        if (index == -1 && !isRoute()) {
            onDebug("find no domain {} in {} with no routeing enabled.", domain, getDomains());
            return null;
        } else if (index == -1) {
            onDebug("try find domain {} in {} with routeing", domain + ROUTE_MARK, getDomains());
            index = getDomains().indexOf(domain + ROUTE_MARK);
        }
        if (index == -1) {
            onDebug("find no domain {} in {} with routeing", domain + ROUTE_MARK, getDomains());
            return null;
        }
        final ConcurrentSkipListSet<Remote> remotes = getRemoteServices().get(index);
        if (remotes.isEmpty()) {
            onDebug("find domain {} in {} with routeing, but found no Remote exists in {}!", domain + ROUTE_MARK, getDomains(), getRemoteServices());
            return null;
        }
        return remotes.first();
    }

    default boolean updateRemoteService(Remote newRemote, Remote oldRemote) {
        boolean updated = false;
        onDebug("before update remote service: {} to {} \n {} {}", newRemote, oldRemote, getDomains(), getRemoteServices());
        if (oldRemote != null) {
            oldRemote.getService().forEach(v -> removeOldRemoteService(v, oldRemote));
        }
        onDebug("after remove old when update remote service: {} to {} \n {} {}", newRemote, oldRemote, getDomains(), getRemoteServices());
        for (String domain : newRemote.getService()) {
            onDebug("before register remote {} with service {}", newRemote.getName(), domain);
            final int index = getOrAddDomain(domain);
            ConcurrentSkipListSet<Remote> remotes = getRemoteServices().get(index);
            if (remotes == null) {
                updated = true;//a new Domain found
                remotes = new ConcurrentSkipListSet<>(Remote.weightComparator);
                getRemoteServices().put(index, remotes);
            }
            remotes.add(newRemote);
        }
        onDebug("after update remote service: \n {} to {} \n {} {} \n domain update status:{}", oldRemote, newRemote, getDomains(), getRemoteServices(), updated);
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


