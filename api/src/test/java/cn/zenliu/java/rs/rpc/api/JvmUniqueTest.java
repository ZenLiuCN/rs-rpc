package cn.zenliu.java.rs.rpc.api;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmUniqueTest {
    String RND = "arnd|dc|Nllq9wtlzpDySAgsNRu7hw==|489f7ff657d2740f|e214fc73";
    String RND_o = "arnd|dc|Nllq9wtlzpDySAgsNRu7hw==|489f7ff657d2740f|e214fc73";
    String NRND = "nornd|dc|Nllq9wtlzpDySAgsNRu7hw==|489f7ff657d2740f|0";
    String NRND_o = "nornd|dc|Nllq9wtlzpDySAgsNRu7hw==|489f7ff657d2740f|0";

    @Test
    @Order(1)
    void generateOne() {
        RND = JvmUnique.uniqueNameWithRandom("arnd");
        NRND = JvmUnique.uniqueNameWithoutRandom("nornd");
        System.out.println(RND);
        System.out.println(NRND);
    }

    @Order(2)
    @Test
    void validateFast() {
        System.out.println(RND);
        System.out.println(NRND);
        final boolean mineFast = JvmUnique.isMineFast(RND);
        final boolean mine = JvmUnique.isMine(NRND);
        assertTrue(mineFast);
        assertTrue(mine);
        assertFalse(JvmUnique.isMineFast(RND_o));
        assertFalse(JvmUnique.isMineFast(NRND_o));
        final JvmUnique.JvmUniqueId jvmUniqueId = JvmUnique.dumpName(NRND).get();
        System.out.println(jvmUniqueId.dump());
        System.out.println(Ticks.from(jvmUniqueId.getTick()));
    }
}