package cn.zenliu.java.mimic.test;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface OuterNestMost {
    long getId();

    String getName();

    OuterMost getOuterMost();

    void setOuterMost(OuterMost om);

    @Getter
    @Builder
    class Outer implements OuterNestMost {
        long id;
        String name;
        @Setter OuterMost outerMost;
    }
}
