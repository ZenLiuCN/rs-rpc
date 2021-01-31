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
        final ConcurrentSkipListSet<Remote> remoteImpls = getRemoteServices().get(index);
        if (remoteImpls.isEmpty()) {
            onDebug("find domain {} in {} with routeing, but found no Remote exists in {}!", domain + ROUTE_MARK, getDomains(), getRemoteServices());
            return null;
        }
        return remoteImpls.first();
    }

    default boolean updateRemoteService(Remote newRemote, Remote oldRemote) {
        boolean updated = false;
        onDebug("before update remote service: {} to {} \n {} {}", newRemote, oldRemote, getDomains(), getRemoteServices());
        if (oldRemote != null) {
            oldRemote.getRoutes().forEach(v -> removeOldRemoteService(v, oldRemote));
        }
        onDebug("after remove old when update remote service: {} to {} \n {} {}", newRemote, oldRemote, getDomains(), getRemoteServices());
        for (String route : newRemote.getRoutes()) {
            onDebug("before register remote {} with service {}", newRemote.getName(), route);
            final int index = getOrAddDomain(route);
            ConcurrentSkipListSet<Remote> remoteImpls = getRemoteServices().get(index);
            if (remoteImpls == null) {
                updated = true;//a new Domain found
                remoteImpls = new ConcurrentSkipListSet<>(Remote.weightComparator);
                getRemoteServices().put(index, remoteImpls);
            }
            remoteImpls.add(newRemote);
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


