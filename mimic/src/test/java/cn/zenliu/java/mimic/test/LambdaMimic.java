package cn.zenliu.java.mimic.test;

import cn.zenliu.java.mimic.api.MimicApi;
import org.jooq.lambda.function.Function3;

import java.io.Serializable;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import static mimic.MimicLambdaUtil.isLambda;
import static mimic.MimicLambdaUtil.prepareLambda;


/**
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-31
 */
public interface LambdaMimic {


    static void main(String[] args) {
        final BiConsumer<Object, Integer> a = (l, r) -> System.out.println(l + "" + r);
        final Supplier<Integer> ac = () -> 1;
        final Function3<Long, Integer, String, String> b = (Serializable & Function3<Long, Integer, String, String>) (l, r, s) -> l + "" + r + s;
        final Integer ai = 1;
        System.out.println("isLambda(ai) = " + isLambda(ai));
        System.out.println("isLambda(ai) = " + isLambda(a));
        System.out.println("isLambda(ai) = " + isLambda(b));
        System.out.println("b.getClass() = " + b.getClass());
        System.out.println("b.getClass() = " + a.getClass());
        System.out.println("b.getClass().getDeclaredMethods() = " + Arrays.toString(b.getClass().getDeclaredMethods()));
        System.out.println("b.getClass() = " + Arrays.toString(b.getClass().getInterfaces()));
        System.out.println("prepareLambda(a) = " + prepareLambda(a));
        System.out.println("prepareLambda(a) = " + prepareLambda(b));
        System.out.println("MimicApi.mimic(a) = " + MimicApi.mimic(a));
        System.out.println("MimicApi.mimic(a) = " + MimicApi.mimic(ac));
        System.out.println("MimicApi.mimic(a) = " + MimicApi.mimic(ac).get());
        System.out.println("MimicApi.mimic(a) = " + MimicApi.mimicOf(ac));
        System.out.println("MimicApi.mimic(a) = " + MimicApi.mimicOf(a));
        System.out.println("MimicApi.mimic(b) = " + MimicApi.mimicOf(b).asLambda().setInvoker(x -> x[0] + "").disguise().apply(1L, 1, "s"));
    }
}
