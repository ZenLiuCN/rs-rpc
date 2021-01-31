package cn.zenliu.java.mimic.test;

import cn.zenliu.java.mimic.api.MimicApi;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.ChainedOptionsBuilder;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * Benchmark                  Mode  Cnt   Score   Error  Units
 * MimicBenchMark.deep2Mimic  avgt   40   9.583 ± 0.998  us/op
 * MimicBenchMark.deep3Mimic  avgt   40  17.738 ± 2.124  us/op
 * MimicBenchMark.lightMimic  avgt   40   3.765 ± 0.534  us/op
 * <p>
 * Benchmark                  Mode  Cnt   Score   Error  Units
 * MimicBenchMark.deep2Mimic  avgt   40  11.011 ± 1.420  us/op
 * MimicBenchMark.deep3Mimic  avgt   40  17.042 ± 2.484  us/op
 * MimicBenchMark.lightMimic  avgt   40   4.033 ± 0.581  us/op
 * <p>
 * Benchmark                  Mode  Cnt   Score   Error  Units
 * MimicBenchMark.deep2Mimic  avgt   40   8.705 ± 0.894  us/op
 * MimicBenchMark.deep3Mimic  avgt   40  14.701 ± 1.898  us/op
 * MimicBenchMark.lightMimic  avgt   40   3.680 ± 0.519  us/op
 * almost: 1:2.8:4.8, should do not contains too much nested data.
 *
 * MimicBenchMark.deep2Mimic      avgt   40  10.232 ± 5.391  us/op
 * MimicBenchMark.deep3Mimic      avgt   40  12.863 ± 1.642  us/op
 * MimicBenchMark.lightMimic      avgt   40   3.701 ± 1.204  us/op
 * MimicBenchMark.readDeep2Mimic  avgt   40   0.807 ± 0.124  us/op
 * MimicBenchMark.readDeep3Mimic  avgt   40   1.323 ± 0.322  us/op
 * MimicBenchMark.readLightMimic  avgt   40   0.383 ± 0.077  us/op
 *
 * @author Zen.Liu
 * @apiNote
 * @since 2021-01-26
 */
public class MimicBenchMark {
    final static int warmUpBatchSize = 20;
    final static int warmUpIterations = 20;
    final static int warmUpTime = 20;
    final static int measurementTime = 20;
    final static int measurementIterations = 20;
    final static int timeoutTime = 20;


    private static void benchRun(@Nullable String methodName, boolean common) throws Exception {
        final ChainedOptionsBuilder opt = new OptionsBuilder()
            .include(MimicBenchMark.class.getName() + (methodName == null || methodName.isEmpty() ? ".*" : "." + methodName));
        //region config
        if (common)
            opt.mode(Mode.AverageTime)
                .timeUnit(TimeUnit.MICROSECONDS)
                .warmupTime(TimeValue.milliseconds(30))
                .warmupIterations(warmUpIterations)
                .warmupBatchSize(warmUpBatchSize)
                .measurementTime(TimeValue.milliseconds(30))
                .measurementIterations(measurementIterations)
                .threads(5)
                .forks(2)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                // .addProfiler(WinPerfAsmProfiler.class)
                //endregion
                ;
        new Runner(opt.build()).run();
    }

    @Test
    public void allBenchmark() throws Exception {
        benchRun("*", true);
    }

    @Benchmark
    public void lightMimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerMost));

    }

    @Benchmark
    public void deep2Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerNestMost));

    }

    @Benchmark
    public void deep3Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerNestDeepMost));

    }


    @Benchmark
    public void readLightMimic(BenchmarkState state, Blackhole bh) {
        bh.consume(state.outerMostMimic.getName());

    }

    @Benchmark
    public void readDeep2Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(state.outerNestMostMimic.getOuterMost().getName());
    }

    @Benchmark
    public void readDeep3Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(state.outerNestDeepMostMimic.getOuterMost().getOuterMost().getName());

    }


    @State(Scope.Thread)
    public static class BenchmarkState {
        OuterMost outerMost;
        OuterNestMost outerNestMost;
        OuterNestDeepMost outerNestDeepMost;
        OuterMost outerMostMimic;
        OuterNestMost outerNestMostMimic;
        OuterNestDeepMost outerNestDeepMostMimic;
        BiFunction<Long, Integer, String> b;
        @Setup(Level.Trial)
        public void initialize() {
            outerMost = OuterMost.Outer.builder().id(123).name("123Name").build();
            outerNestMost = OuterNestMost.Outer.builder().id(1234).name("1234Name").outerMost(outerMost).build();
            outerNestDeepMost = OuterNestDeepMost.Outer.builder().id(12345).name("12345Name").outerMost(outerNestMost).build();

            outerMostMimic = MimicApi.mimic(outerMost);
            outerNestMostMimic = MimicApi.mimic(outerNestMost);
            outerNestDeepMostMimic = MimicApi.mimic(outerNestDeepMost);
            b = (l, r) -> l + "" + r;
        }
    }
}
