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

/**
 * # Run complete. Total time: 00:07:03
 * <p>
 * REMEMBER: The numbers below are just data. To gain reusable insights, you need to follow up on
 * why the numbers are the way they are. Use profilers (see -prof, -lprof), design factorial
 * experiments, perform baseline and negative tests that provide experimental control, make sure
 * the benchmarking environment is safe on JVM/OS/HW level, ask for reviews from the domain experts.
 * Do not assume the numbers tell you what you want them to tell.
 * <p>
 * Benchmark                  Mode  Cnt   Score    Error  Units
 * MimicBenchMark.deep2Mimic  avgt  100  19.732 ± 10.214  us/op
 * MimicBenchMark.deep3Mimic  avgt  100  30.132 ±  9.448  us/op
 * MimicBenchMark.lightMimic  avgt  100  30.017 ± 80.168  us/op
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
                .warmupTime(TimeValue.milliseconds(20))
                .warmupIterations(warmUpIterations)
                .warmupBatchSize(warmUpBatchSize)
                .measurementTime(TimeValue.milliseconds(10))
                .measurementIterations(measurementIterations)
                .threads(5)
                .forks(5)
                .shouldFailOnError(true)
                .shouldDoGC(true)
                //.jvmArgs("-XX:+UnlockDiagnosticVMOptions", "-XX:+PrintInlining")
                //.addProfiler(WinPerfAsmProfiler.class)
                //endregion
                ;
        new Runner(opt.build()).run();
    }

    @Test
    public void allBenchmark() throws Exception {
        benchRun("", true);
    }


    @Benchmark
    public void lightMimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerMost));

    }

    @Benchmark
    public void deep3Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerNestDeepMost));

    }

    @Benchmark
    public void deep2Mimic(BenchmarkState state, Blackhole bh) {
        bh.consume(MimicApi.mimic(state.outerNestMost));

    }


    @State(Scope.Thread)
    public static class BenchmarkState {
        OuterMost outerMost;
        OuterNestMost outerNestMost;
        OuterNestDeepMost outerNestDeepMost;


        @Setup(Level.Trial)
        public void initialize() {
            outerMost = OuterMost.Outer.builder().id(123).name("123Name").build();
            outerNestMost = OuterNestMost.Outer.builder().id(1234).name("1234Name").outerMost(outerMost).build();
            outerNestDeepMost = OuterNestDeepMost.Outer.builder().id(12345).name("12345Name").outerMost(outerNestMost).build();
        }
    }
}
