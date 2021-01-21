package cn.zenliu.java.rs.rpc.core;

import cn.zenliu.java.rs.rpc.api.Ticks;
import lombok.Builder;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-18
 */
@Builder
@Getter
public class Meta {
    /**
     * domain: ServiceClassCanonicalName#Method<MethodArgumentCount>
     */
    final String sign;
    @Builder.Default final boolean track = false;
    @Builder.Default final String uuid = UUID.randomUUID().toString();
    /**
     * trace information: (timestamp,nodeScopeName)
     */
    @Builder.Default final Map<Long, String> trace = new HashMap<>();

    public Meta addTrace(@NotNull String scope) {
        trace.put(Ticks.fromNowUTC(), scope);
        return this;
    }

    @Override
    public String toString() {
        return "\n--------------REQUEST META----------------" +
            "\n domain=" + sign +
            "\n uuid=" + uuid +
            "\n trace=" + trace +
            "\n----------------------------------------";

    }
}
