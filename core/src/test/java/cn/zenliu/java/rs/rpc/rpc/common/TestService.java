package cn.zenliu.java.rs.rpc.rpc.common;


import cn.zenliu.java.rs.rpc.api.Result;

public interface TestService {
    Long getInt();

    void ffi(int i);

    Result<Long> getResult(Long arg);
}
