package cn.zenliu.java.mimic.test;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface OuterMost {
    long getId();

    String getName();

    @Getter
    @Builder
    class Outer implements OuterMost {
        long id;
        String name;
    }
}
