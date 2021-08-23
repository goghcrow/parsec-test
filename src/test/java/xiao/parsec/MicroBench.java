package xiao.parsec;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;

/**
 * @author chuxiaofeng
 */
@SuppressWarnings("WeakerAccess")
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
public class MicroBench {

//    static String json = Utils.resource("/small.json");
    static String json = Utils.resource("/large.json");

    @Benchmark public Object json() { return JSON.Parse(json); }
    @Benchmark public Object json1() { return JSON1.Parse(json); }
    @Benchmark public Object json2() { return JSON2.Parse(json); }
    @Benchmark public Object json3() { return JSON3.Parse(json); }
    @Benchmark public Object json4() { return JSON4.Parse(json); }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(MicroBench.class.getSimpleName())
                .forks(1)
                .warmupIterations(5)
                .measurementIterations(5)
                .build();

        new Runner(opt).run();
    }
}
