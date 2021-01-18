package cn.zenliu.java.rs.rpc.spring;

import cn.zenliu.java.rs.rpc.api.Scope;
import cn.zenliu.java.rs.rpc.core.Rpc;
import cn.zenliu.java.rs.rpc.core.ScopeImpl;
import cn.zenliu.java.rs.rpc.core.Service;
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
 * @since 2021-01-17
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({MBeanEnableRPC.RpcMBean.class})
public @interface MBeanEnableRPC {
    @ManagedResource(description = "管理RPC")
    final class RpcMBean {
        final ApplicationContext ctx;

        public RpcMBean(ApplicationContext ctx) {
            this.ctx = ctx;
        }

        @ManagedOperation(description = "查看当前启用的Scope名称")
        public Set<String> getScopeName() {
            return Rpc.scope;
        }


        @ManagedOperation(description = "查看或设置Debug")
        @ManagedOperationParameters({
            @ManagedOperationParameter(name = "name", description = "scope简化名称"),
            @ManagedOperationParameter(name = "debug", description = "Null为查看,否则为设置"),
        })
        public Boolean actDebug(@NotNull String name, @Nullable Boolean debug) {
            final ScopeImpl bean = getBean(name);
            if (debug != null) bean.getDebug().set(debug);
            return bean.getDebug().get();
        }

        @ManagedOperation(description = "查看或设置Timeout")
        @ManagedOperationParameters({
            @ManagedOperationParameter(name = "name", description = "scope简化名称"),
            @ManagedOperationParameter(name = "timeout", description = "Null为查看,否则为设置"),
        })
        public Duration actTimeout(@NotNull String name, @Nullable Duration timeout) {
            final ScopeImpl bean = getBean(name);
            if (timeout != null) bean.getTimeout().set(timeout);
            return bean.getTimeout().get();
        }

        @ManagedOperation(description = "查看本地服务端")
        @ManagedOperationParameter(name = "name", description = "scope简化名称")
        public Map<String, Boolean> localServices(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getLocalServers()).map(t -> t.map2(Disposable::isDisposed)).toMap(Tuple2::v1, Tuple2::v2);
        }

        @ManagedOperation(description = "查看本地注册的Service")
        @ManagedOperationParameter(name = "name", description = "scope简化名称")
        public Set<String> localServiceName(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return bean.getLocalServices();
        }

        @ManagedOperation(description = "查看远程服务")
        @ManagedOperationParameter(name = "name", description = "scope简化名称")
        public Map<Integer, Map<String, ?>> remotesRegistry(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getRemoteRegistry()).map(t -> t.map2(Service::dumpMeta))
                .toMap(Tuple2::v1, Tuple2::v2);
        }

        @ManagedOperation(description = "查看远程服务注册")
        @ManagedOperationParameter(name = "name", description = "scope简化名称")
        public Map<String, List<Map<String, Object>>> remotes(@NotNull String name) {
            final ScopeImpl bean = getBean(name);
            return Seq.seq(bean.getRemoteServices())
                .map(t -> t.map2(x -> Seq.seq(x).map(Service::dumpMeta).toList()))
                .toMap(Tuple2::v1, Tuple2::v2);
        }


        private ScopeImpl getBean(String name) {
            if (name.equals(Rpc.GLOBAL_NAME)) {
                return (ScopeImpl) Rpc.Global;
            }
            return (ScopeImpl) ctx.getBean(name, Scope.class);
        }
    }
}
