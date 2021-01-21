package cn.zenliu.java.rs.rpc.core;

import io.netty.buffer.ByteBufUtil;
import io.rsocket.Payload;
import io.rsocket.util.DefaultPayload;
import lombok.Builder;
import lombok.ToString;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-18
 */
@Builder
@ToString
public class ServMeta implements Serializable {
    private static final long serialVersionUID = -3906227121661168621L;
    /**
     * Remote Name
     */
    final String name;
    /**
     * Supported Service Domain (with out methods)
     */
    @Builder.Default final List<String> service = new ArrayList<>();
    @Builder.Default final Set<String> known = new HashSet<>();

    public static Payload build(String name, Set<String> service, Set<String> known) {
        return DefaultPayload.create(
            DefaultPayload.EMPTY_BUFFER,
            ByteBuffer.wrap(Proto.to(ServMeta.builder().name(name).service(new ArrayList<>(service)).known(new HashSet<>(known)).build()))
        );
    }

    public static ServMeta parse(Payload p) {
        try {
            return Proto.from(ByteBufUtil.getBytes(p.sliceMetadata()), ServMeta.class);
        } finally {
            p.release();
        }
    }
}
