package cn.zenliu.java.rs.rpc.spring;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
public interface RsRpcServiceBeanNameProcessor {
    @NotNull String decide(Class<?> type);
}
