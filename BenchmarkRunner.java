import java.nio.file.Path;
import java.nio.file.Paths;

public class BenchmarkRunner {

    private final Path directory;

    public BenchmarkRunner(String directory, long seed) {
        this.directory = Paths.get(directory);
    }

    // PUBLIC API
    public void runFixedBenchmarks(int warmup, int runs) {
        System.out.println("\n================ FIXED BENCHMARK ================\n");

        warmupFixed(warmup);

        double shortest = avgShortest(runs);
        double consonants = avgConsonantsFixed(runs, 3);
        double suffix = avgSuffixFixed(runs, "test");
        double allLines = avgAllLines(runs);

        print("FIXED", shortest, consonants, suffix, allLines);
    }

    // WARMUP
    private void warmupFixed(int warmup) {
        for (int i = 0; i < warmup; i++) {
            ConcurrentTextSearch.shortestWord(directory);
            ConcurrentTextSearch.wordWithConsonants(directory, 3);
            ConcurrentTextSearch.wordsEndingWith(directory, "warmup", 5);
            ConcurrentTextSearch.findWordsCommonToAllLines(directory);
        }
    }

    // FIXED BENCHMARKS
    private double avgShortest(int runs) {
        return average(runs, () ->
                ConcurrentTextSearch.shortestWord(directory));
    }

    private double avgConsonantsFixed(int runs, int n) {
        return average(runs, () ->
                ConcurrentTextSearch.wordWithConsonants(directory, n));
    }

    private double avgSuffixFixed(int runs, String suffix) {
        return average(runs, () ->
                ConcurrentTextSearch.wordsEndingWith(directory, suffix, 10));
    }

    private double avgAllLines(int runs) {
        return average(runs, () ->
                ConcurrentTextSearch.findWordsCommonToAllLines(directory));
    }

    // CORE TIMING
    private double average(int runs, Task task) {
        long total = 0;

        for (int i = 0; i < runs; i++) {
            long start = System.nanoTime();
            task.run();
            total += System.nanoTime() - start;
        }

        return (total / (double) runs) / 1_000_000.0; // ms
    }

    // OUTPUT
    private void print(String mode,
                       double shortest,
                       double consonants,
                       double suffix,
                       double allLines) {

        System.out.println("Mode: " + mode);
        System.out.println("--------------------------------");

        System.out.printf("Shortest Word:    %.3f ms%n", shortest);
        System.out.printf("Consonants:       %.3f ms%n", consonants);
        System.out.printf("Suffix Search:    %.3f ms%n", suffix);
        System.out.printf("All Lines:        %.3f ms%n", allLines);

        System.out.println("--------------------------------\n");
    }

    @FunctionalInterface
    private interface Task {
        void run();
    }
}