package cn.zenliu.java.rs.rpc.core.proto;

import cn.zenliu.java.rs.rpc.api.Tick;
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

    final String from;
    @Builder.Default final long tick = Tick.fromNowUTC();
    @Builder.Default final boolean trace = false;
    @Builder.Default final String uuid = UUID.randomUUID().toString();
    /**
     * trace information: (timestamp,nodeScopeName)
     */
    @Builder.Default final Map<Long, String> link = new HashMap<>();

    public Meta addTrace(@NotNull String scope) {
        link.put(Tick.fromNowUTC(), scope);
        return this;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("META@").append(sign).append('@').append(uuid).append("{").append(from).append('@').append(Tick.from(tick).getKey()).append(":T:").append(trace).append('}');
        if (link != null && !link.isEmpty()) {
            link.forEach((k, v) -> {
                builder.append('[').append(Tick.from(k).getKey()).append(':').append(v).append(']').append(">");
            });
            builder.setLength(builder.length() - 1);
        }
        return builder.toString();
    }

    public String cost() {
        if (link == null || link.isEmpty()) {
            return "Nan";
        }
        if (link.size() == 1) {
            return Tick.between(tick, link.keySet().iterator().next()).getNano() / 1000.0 + " μs";
        }
        final long[] objects = link.keySet().stream().sorted().mapToLong(x -> x).toArray();
        final long l1 = objects[0];
        final long l2 = objects[objects.length - 1];
        return Tick.between(tick, Math.max(l1, l2)).getNano() / 1000.0 + " μs";
    }

    public String costNow() {
        return Tick.betweenNow(tick).getNano() / 1000.0 + " μs";
    }
}
