package cn.zenliu.java.rs.rpc.rpc.common;


import cn.zenliu.java.rs.rpc.api.Result;

public class TestServiceImpl implements TestService {
    @Override
    public Long getInt() {
        return 1L;
    }

    @Override
    public Result<Long> getResult(Long arg) {
        return arg > 0 ? Result.ok(arg) : Result.error(new IllegalStateException("ososo"));
    }
}
