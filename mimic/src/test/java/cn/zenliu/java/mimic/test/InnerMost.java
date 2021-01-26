package cn.zenliu.java.mimic.test;

import lombok.Builder;
import lombok.Getter;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface InnerMost {
    long getId();

    String getName();

    @Getter
    @Builder
    class Inner implements InnerMost {
        long id;
        String name;
    }
}
