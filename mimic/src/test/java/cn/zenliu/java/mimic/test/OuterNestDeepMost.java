package cn.zenliu.java.mimic.test;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public interface OuterNestDeepMost {
    long getId();

    String getName();

    OuterNestMost getOuterMost();

    void setOuterMost(OuterNestMost om);

    @Getter
    @Builder
    class Outer implements OuterNestDeepMost {
        long id;
        String name;
        @Setter OuterNestMost outerMost;
    }
}
