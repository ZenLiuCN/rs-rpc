package cn.zenliu.java.rs.rpc.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

class TicksTest {

    @Test
    void test2from() {
        final Instant now = Instant.now();
        System.out.println(now);
        System.out.println(Tick.from(now, false));
    }

    @Test
    void test1From() {
        final Instant instant = Instant.parse("2021-01-19T13:03:49.555Z");
        final Map.Entry<Instant, Boolean> result = Tick.from(5249152600722937904L);
        System.out.println(result.getKey());
        System.out.println(instant);
    }

    @Test
    void fromNowUTC() {
    }
}