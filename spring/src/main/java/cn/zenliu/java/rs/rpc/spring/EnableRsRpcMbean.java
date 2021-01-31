package cn.zenliu.java.rs.rpc.spring;

import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.element.Remote;
import cn.zenliu.java.rs.rpc.core.impl.ScopeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jooq.lambda.Seq;
import org.jooq.lambda.tuple.Tuple2;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jmx.export.annotation.ManagedOperation;
import org.springframework.jmx.export.annotation.ManagedOperationParameter;
import org.springframework.jmx.export.annotation.ManagedOperationParameters;
import org.springframework.jmx.export.annotation.ManagedResource;
import reactor.core.Disposable;

import java.lang.annotation.*;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author Zen.Liu
 * @version 1.0
 * @apiNote
 * @since 2021-01-28
 */


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({EnableRsRpcMbean.RpcMBean.class})
public @interface EnableRsRpcMbean {
    @ManagedResource(description = "RsRpcManagement")
    final class RpcMBean {
        final ApplicationContext ctx;

        public RpcMBean(ApplicationContext ctx) {
            this.ctx = ctx;
        }

        @ManagedOperation(description = "check all scope names")
        public Set<String> getScopeName() {
            return Rpc.scopes.keySet();
        }


        @ManagedOperation(description = "view or set debug")
        @ManagedOperationParameters({
            @ManagedOperationParameter(name = "name", description = "scope simple name"),
            @ManagedOperationParameter(name = "debug", description = "null means view, or else is set"),
        })
        public Boolean actDebug(@NotNull String name, @Nullable Boolean debug) {
            final ScopeImpl bean = getBean(name);
            if (debug != null) bean.getDebug().set(debug);
            return bean.getDebug().get();
        }

        @ManagedOperation(description = "view or set timeout")
        @ManagedOperationParameters({
            @ManagedOperationParameter(name = "name", description = "scope simple name"),
            @ManagedOperationParameter(name = "timeout", description = "null means view, or else is set"),
        })
        public Duration actTimeout(@NotNull String name, @Nullable Duration timeout) {
            final ScopeImpl bean = getBean(name);
            if (timeout != null) bean.getTimeout().set(timeout);
            return bean.getTimeout().get();
        }

        @ManagedOperation(description = "view local RSocket Servers")
        @ManagedOperationParameter(name = "name", description = "scope simple name")
        public Map<String, Boolean> localServices(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getServers()).map(t -> t.map2(Disposable::isDisposed)).toMap(Tuple2::v1, Tuple2::v2);
        }

        @ManagedOperation(description = "check local registered services")
        @ManagedOperationParameter(name = "name", description = "scope simple name")
        public Set<String> localServiceName(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return bean.getServiceName(null);
        }

        @ManagedOperation(description = "view remotes")
        @ManagedOperationParameter(name = "name", description = "scope simple name")
        public Map<String, Map<String, ?>> remotesRegistry(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getRemoteNames().getValue()).zipWithIndex()
                .map(t -> t.map2(i -> bean.getRemotes().get(i.intValue())))
                .map(t -> t.map2(Remote::dumpRemote))
                .toMap(Tuple2::v1, Tuple2::v2);
        }

        @ManagedOperation(description = "view remotes services")
        @ManagedOperationParameter(name = "name", description = "scope simple name")
        public Map<String, List<Map<String, Object>>> remotes(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getRemoteServices())
                .map(t -> t.map1(i -> bean.getDomains().getValue().get(i)).map2(x -> Seq.seq(x).map(Remote::dumpRemote).toList()))
                .toMap(Tuple2::v1, Tuple2::v2);
        }

        @ManagedOperation(description = "view local proxy services")
        @ManagedOperationParameter(name = "name", description = "scope simple name")
        public List<String> proxies(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return bean.getProxiesList();
        }

        private ScopeImpl getBean(String name) {
            if (name.equals(Rpc.GLOBAL_NAME)) {
                return (ScopeImpl) Rpc.Global;
            }
            return (ScopeImpl) Rpc.scopes.getOrDefault(name, null);
        }
    }
}
