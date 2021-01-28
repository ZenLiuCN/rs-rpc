package cn.zenliu.java.rs.rpc.spring;

import java.util.List;

/**
 * supplier of a List of interfaces, those wanna to as a proxy rpc service bean.
 */
@FunctionalInterface
public interface RsRpcServiceImport {
    List<Class<?>> imported();
}
