package cn.zenliu.java.rs.rpc.spring;

import java.util.List;

/**
 * support a list of Classes in IOC want to exposed as Rpc Services.
 */
@FunctionalInterface
public interface RsRpcServiceExpose {
    List<Class<?>> expose();
}
