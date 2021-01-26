package cn.zenliu.java.mimic.test;

import cn.zenliu.java.mimic.api.MimicApi;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import io.protostuff.LinkedBuffer;
import io.protostuff.runtime.DefaultIdStrategy;
import io.protostuff.runtime.IdStrategy;
import io.protostuff.runtime.RuntimeSchema;
import mimic.Delegator;
import mimic.Mimic;
import mimic.Proxy;
import org.junit.jupiter.api.Test;

import static io.protostuff.ProtostuffIOUtil.mergeFrom;
import static io.protostuff.ProtostuffIOUtil.toByteArray;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public class MimicTest {
    @Test
    void simpleProxy() {
        final Proxy<OuterMost> proxy = MimicApi.proxyOf(OuterMost.class);
        proxy.set("Id", 123L).set("Name", "aName");
        final OuterMost delegate = proxy.disguise();
        assertEquals(123L, delegate.getId());
        assertEquals("aName", delegate.getName());
        final OuterMost mimic = MimicApi.mimic(delegate);
        assertEquals(123L, mimic.getId());
        assertEquals("aName", mimic.getName());
        System.out.println(mimic);
        System.out.println(delegate);
    }

    @Test
    void level2Proxy() {
        final Proxy<OuterNestMost> proxy = MimicApi.proxyOf(OuterNestMost.class);
        proxy.set("Id", 123L).set("Name", "aName");
        final OuterNestMost delegate = proxy.disguise();
        assertEquals(123L, delegate.getId());
        assertEquals("aName", delegate.getName());
        final OuterNestMost mimic = MimicApi.mimic(delegate);
        assertEquals(123L, mimic.getId());
        assertEquals("aName", mimic.getName());
        final Proxy<OuterMost> om = MimicApi.proxyOf(OuterMost.class);
        om.set("Id", 123L).set("Name", "aName");
        mimic.setOuterMost(om.disguise());
        System.out.println(mimic);
        System.out.println(mimic.getOuterMost());
        System.out.println(delegate);
        final Delegator<?> reveal = MimicApi.reveal(mimic);
        if (reveal != null) System.out.println(reveal.dump());
    }

    @Test
    void level3Proxy() {
        final Proxy<OuterNestMost> proxy = MimicApi.proxyOf(OuterNestMost.class);
        proxy.set("Id", 123L).set("Name", "aName");
        final Proxy<OuterMost> om = MimicApi.proxyOf(OuterMost.class);
        om.set("Id", 123L).set("Name", "aName");
        proxy.set("OuterMost", om.disguise());
        final Proxy<OuterNestDeepMost> p = MimicApi.proxyOf(OuterNestDeepMost.class);
        p.set("Id", 12315L);
        p.set("Name", "aName");
        p.set("OuterMost", proxy.disguise());
        final OuterNestMost delegate = proxy.disguise();
        assertEquals(delegate, proxy.disguise());
        assertEquals(123L, delegate.getId());
        assertEquals("aName", delegate.getName());
        final OuterNestDeepMost mimic = MimicApi.mimic(p.disguise());
        assertEquals(12315L, mimic.getId());
        assertEquals("aName", mimic.getName());
        System.out.println(mimic);
        System.out.println(mimic.getOuterMost());
        System.out.println(delegate);
        System.out.println("dump");
        final Delegator<?> first = MimicApi.reveal(mimic);
        if (first != null) System.out.println(first.dump());
        Delegator<?> reveal = MimicApi.reveal(mimic.getOuterMost());
        if (reveal != null) System.out.println(reveal.dump());
        reveal = MimicApi.reveal(((OuterNestMost) reveal.disguise()).getOuterMost());
        if (reveal != null) System.out.println(reveal.dump());
        final RuntimeSchema<Mimic> schema = RuntimeSchema.createFrom(Mimic.class, STRATEGY);
        final byte[] bytes = toByteArray((Mimic) first, schema, LinkedBuffer.allocate(512));
        System.out.println(bytes.length + "\n" + ByteBufUtil.prettyHexDump(Unpooled.copiedBuffer(bytes)));
        final Mimic msg = schema.newMessage();
        mergeFrom(bytes, msg, schema);
        System.out.println(msg.disguise());
        System.out.println(msg.disguise().equals(mimic));
    }

    static final DefaultIdStrategy STRATEGY = new DefaultIdStrategy(
        IdStrategy.DEFAULT_FLAGS
            | IdStrategy.ALLOW_NULL_ARRAY_ELEMENT
            | IdStrategy.MORPH_COLLECTION_INTERFACES
            | IdStrategy.MORPH_MAP_INTERFACES
            | IdStrategy.MORPH_NON_FINAL_POJOS
    );
}
