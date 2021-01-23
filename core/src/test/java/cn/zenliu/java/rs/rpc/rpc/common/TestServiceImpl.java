package cn.zenliu.java.rs.rpc.rpc.common;


import cn.zenliu.java.rs.rpc.api.Result;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TestServiceImpl implements TestService {
    @Override
    public Long getInt() {
        log.info("current thread {}", Thread.currentThread().getId());
        return 1L;
    }

    @Override
    public void ffi(int i) {
        log.error("ffi {}", i);
    }

    @Override
    public Result<Long> getResult(Long arg) {
        log.info("current thread {}", Thread.currentThread().getId());
        return arg > 0 ? Result.ok(arg) : Result.error(new IllegalStateException("ososo"));
    }
}
