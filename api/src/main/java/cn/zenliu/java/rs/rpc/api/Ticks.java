package cn.zenliu.java.rs.rpc.api;

import java.time.Instant;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map;

/**
 * this is compact format of C# ticks
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-17
 */
public interface Ticks {
    long KindUtc = 0x4000000000000000L;//1
    long KindLocal = 0x8000000000000000L;//2
    long TicksMask = 0x3FFFFFFFFFFFFFFFL;
    int KindShift = 62;
    int tickPerSec = 10_000_000;
    long base = -62135596800L;

    /**
     * convert ticks to Instant
     *
     * @param ticks long from C# DateTime.toBinary()
     * @return (timestamp, isLocalTime)
     */
    static Map.Entry<Instant, Boolean> from(long ticks) {
        final long flag = ticks >>> KindShift;
        final long tick = ticks & TicksMask;
        final long sec = tick / tickPerSec;
        final long nano = (tick % tickPerSec) * 100;
        return new SimpleEntry<>(Instant.ofEpochSecond(sec + base, nano), flag == 2L);
    }

    /**
     * create ticks from instant
     *
     * @param instant source instant
     * @param isLocal dose use local flag
     * @return ticks
     */
    static long from(Instant instant, boolean isLocal) {
        long nano = instant.getNano() / 100;
        long sec = instant.getEpochSecond() - base * tickPerSec;
        long tick = sec + nano;
        return tick | (isLocal ? KindLocal : KindUtc);
    }

    static long fromNowUTC() {
        return from(Instant.now(), false);
    }
}
