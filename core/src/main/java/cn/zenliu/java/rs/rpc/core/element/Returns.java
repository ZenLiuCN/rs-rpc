package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.api.Result;
import cn.zenliu.java.rs.rpc.api.Tick;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-02
 */
@Builder
public final class Returns implements Serializable {
    @Getter @Builder.Default final long tick = Tick.fromNowUTC();
    @Getter final Result<Object> response;
    @Getter final Object element;
}
