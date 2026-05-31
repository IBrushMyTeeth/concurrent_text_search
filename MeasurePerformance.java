public class MeasurePerformance {
    public static void main(String[] args) {
        BenchmarkRunner runner = new BenchmarkRunner("data/", 42);
        runner.runFixedBenchmarks(5, 10);
    }
}