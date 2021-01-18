package cn.zenliu.java.rs.rpc.api;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JvmUniqueTest {
    String RND = "arnd|1a20|Nllq9wtlzpDySAgsNRu7hw==|64477ee1";
    String RND_o = "arnd|1a20|Nllq9wtlzpDySAgsNRu7hw==|64477ee1";
    String NRND = "nornd|0a20|Nllq9wtlzpDySAgsNRu7hw==|0";
    String NRND_o = "nornd|0a20|Nllq9wtlzpDySAgsNRu7hw==|0";

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
    }
}