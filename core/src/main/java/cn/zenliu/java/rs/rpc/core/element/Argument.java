package cn.zenliu.java.rs.rpc.core.element;

import cn.zenliu.java.rs.rpc.api.Tick;
import lombok.Builder;
import lombok.Getter;

import java.io.Serializable;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-02-01
 */
@Builder
public final class Argument implements Serializable {
    private static final long serialVersionUID = 2221392751713087472L;
    @Getter @Builder.Default final long tick = Tick.fromNowUTC();
    @Getter final Object[] arguments;

    public boolean isEmpty() {
        return arguments == null || arguments.length == 0;
    }
}
