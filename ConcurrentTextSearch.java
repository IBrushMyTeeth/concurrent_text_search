import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ConcurrentTextSearch {

	// Entry point: parses command-line arguments and dispatches tasks
	public static void main(String[] args) {

		if (args.length == 0) {
			printHelp();
			return;
		}

		String command = args[0];

		switch (command) {

			case "help" -> printHelp();

			case "shortestWord" -> {
				requireArgs(args, 2, "Usage: shortestWord <directory>");

				String result = shortestWord(Paths.get(args[1]));
				System.out.println("Shortest word: " + result);
			}

			case "consonants" -> {
				requireArgs(args, 3, "Usage: consonants <directory> <numberOfConsonants>");

				int n = Integer.parseInt(args[2]);

				Optional<LocatedWord> result =
						wordWithConsonants(Paths.get(args[1]), n);

				result.ifPresentOrElse(
						w -> System.out.println(
								"Found: " + w.getWord()
										+ " in " + w.getFilepath()
										+ ":" + w.getLine()
						),
						() -> System.out.println("No word found with " + n + " consonants.")
				);
			}

			case "allLines" -> {
				requireArgs(args, 2, "Usage: allLines <directory>");

				List<LocatedWord> results =
						findWordsCommonToAllLines(Paths.get(args[1]));

				System.out.println("Found " + results.size() + " words:");
				results.forEach(w ->
						System.out.println(w.getWord() + ":" + w.getFilepath())
				);
			}

			case "suffix" -> {
				requireArgs(args, 4, "Usage: suffix <directory> <suffix> <limit>");

				int limit = Integer.parseInt(args[3]);

				List<LocatedWord> results =
						wordsEndingWith(Paths.get(args[1]), args[2], limit);

				if (results.size() > limit) {
					System.out.println("WARNING: More than " + limit + " results computed.");
				}

				results.forEach(w ->
						System.out.println(
								w.getWord() + ":" +
										w.getFilepath() + ":" +
										w.getLine()
						)
				);
			}

			default -> {
				System.out.println("Unknown command: " + command);
				printHelp();
			}
		}
	}

    // Finds the shortest word across all .txt files in a directory tree
    // Uses a work-stealing pool to parallelize file-level processing
    // Each file is processed independently, and results are merged as they complete
    // Ties are resolved lexicographically
    public static String shortestWord(Path dir) {

        // A work stealing pool is highly efficient for compute-intensive tasks
        // Unlike standard thread pools, when a thread runs out of work, it looks
        // at the queues of other processors and steals from the end of their queues
        int cores = Runtime.getRuntime().availableProcessors();
        ExecutorService pool = Executors.newWorkStealingPool(cores);
        CompletionService<String> completionService = new ExecutorCompletionService<>(pool);

        // current global shortest word
        String shortest = null;

        try (Stream<Path> paths = Files.walk(dir)) {

            long pendingTasks = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .map(path -> completionService.submit(() -> shortestWordInFile(path)))
                    .count();

            while (pendingTasks > 0) {

                String current = completionService.take().get();

                // compare returned word from file to the global shortest word
                if (current != null &&
                        (shortest == null
                                || current.length() < shortest.length()
                                || (current.length() == shortest.length()
                                && current.compareTo(shortest) < 0))) {
                    shortest = current;
                }

                pendingTasks--;
            }

        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                pool.shutdown();
                pool.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return shortest;
    }

    // Searches for a word containing exactly N consonants
    // Uses virtual threads for lightweight parallel file processing
    // Returns immediately once a valid match is found (early termination strategy)
    public static Optional<LocatedWord> wordWithConsonants(Path dir, int numberOfConsonants) {

        // The executor virtualThreadPerTask submits each task as a virtual thread
        // Virtual threads are cheaper to shutdown and cancel on early exit
        // Virtual threads excel for I/O heavy tasks, and showed better results
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CompletionService<LocatedWord> completionService = new ExecutorCompletionService<>(pool);

        try (Stream<Path> paths = Files.walk(dir)) {

            long pendingTasks = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .map(path -> completionService.submit(
                            () -> findFirstWithConsonantsInFile(path, numberOfConsonants)))
                    .count();

            while (pendingTasks > 0) {

                LocatedWord result = completionService.take().get();

                // if the retrieved result from a file is valid
                // stop all other work and exit early
                if (result != null) {
                    pool.shutdownNow();
                    return Optional.of(result);
                }

                pendingTasks--;
            }

        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                pool.shutdownNow();
                pool.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return Optional.empty();
    }

    // Finds words that appear in every line of each file
    // Case-insensitive comparison is applied per file
    // Uses parallelStream to process files concurrently via ForkJoinPool
    public static List<LocatedWord> findWordsCommonToAllLines(Path dir) {

        try (Stream<Path> paths = Files.walk(dir)) {

            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    // Create a synchronization point here
                    // Collect all paths before moving on
                    .collect(Collectors.toList())
                    // Process each file in parallel
                    .parallelStream()
                    .flatMap(path -> commonToAllLinesInFile(path).stream())
                    // Flatten and return a single list of results
                    .collect(Collectors.toList());

        } catch (Exception e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // Finds words ending with a given suffix across files
    // Stops early once the requested limit is reached
    // Uses virtual threads and shared atomic flag for cooperative cancellation
    public static List<LocatedWord> wordsEndingWith(Path dir, String suffix, int limit) {

        // Shared resources
        // The array found is used to store valid instances
        // The atomic boolean is a shared flag for early exit 
        List<LocatedWord> found = new ArrayList<>();
        AtomicBoolean done = new AtomicBoolean(false);

        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        CompletionService<Void> completionService = new ExecutorCompletionService<>(pool);

        try (Stream<Path> paths = Files.walk(dir)) {

            long pendingTasks = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".txt"))
                    .map(path -> completionService.submit(() -> {
                        wordsEndingWithInFile(path, suffix, found, limit, done);
                        return null;
                    }))
                    .count();

            while (pendingTasks > 0) {

                completionService.take().get();
                pendingTasks--;

                // If there are enough words, dont pend the rest
                if (done.get()) {
                    break;
                }
            }

        } catch (InterruptedException | ExecutionException | IOException e) {
            e.printStackTrace();
        } finally {
            try {
                pool.shutdownNow();
                pool.awaitTermination(100, TimeUnit.MILLISECONDS);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return found;
    }

    // Processes a single file and returns its shortest word
    // Uses parallel stream for intra-file acceleration on large inputs
    private static String shortestWordInFile(Path path) {

        try (Stream<String> lines = Files.lines(path)) {

            return lines
				// Parallelize processing of lines within the file via ForkJoin pool
                // To efficiently distribute independent workloads
				// The nested parallelizm actually improved performance on large files
                // and user time got more consistent and 
                    .parallel()
                    .flatMap(line -> WordUtils.extractWords(line).stream())

                    // Find the shortest word among all words
                    // If multiple words have the same length
                    // Pick the lexicographically smallest
                    .min(Comparator
                            .comparingInt(String::length)
                            .thenComparing(String::compareTo))
                    .orElse(null);

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // Scans a file line-by-line and returns the first word
    // that matches the required consonant count
    // Supports early exit via thread interruption
    private static LocatedWord findFirstWithConsonantsInFile(Path path, int consonants) {

		// Use a bufferedReader to read the file line by line
		// This is an alternative to Files.lines which provides cleaner control
        try (BufferedReader reader = Files.newBufferedReader(path)) {

            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {

                // If the thread was interrupted (another thread finished faster)
                // we should immediately return null
                if (Thread.currentThread().isInterrupted()) {
                    return null;
                }

                lineNum++;

                // List of words -> Stream -> Count consonants for each
                String match = WordUtils.extractWords(line)
                        .stream()
                        .filter(word ->
                                WordUtils.countConsonants(word) == consonants)
                        .findFirst()
                        .orElse(null);
                
                // If match is not null, a valid word was found
                if (match != null) {
                    return new LocatedWord(match, path, lineNum);
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    // Computes words that appear on every line of a file
    // Uses set intersection across all lines for efficient filtering
    private static List<LocatedWord> commonToAllLinesInFile(Path path) {

        // Buffered reader is also prefered to Files.lines
        // since it is very practical for early exits
        try (BufferedReader reader = Files.newBufferedReader(path)) {

            // The set survivors is a list of words which have been on every line so far
            Set<String> survivors = null;
            String line;

            // Process each line -> Map to lower case -> Intersect with survivors
            while ((line = reader.readLine()) != null) {

                Set<String> current = WordUtils.extractWords(line)
                        .stream()
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());

                // If null, initialize
                if (survivors == null) {
                    survivors = new HashSet<>(current);
                } else {
                    // Intersection logic
                    survivors.retainAll(current);
                }
                // If survivors is already empty exit without processing the rest
                if (survivors.isEmpty()) {
                    return List.of();
                }
            }

            if (survivors == null) {
                return List.of();
            }

            // Convert the survivors into a list of LocatedWords
            return survivors.stream()
                    .map(word -> new LocatedWord(word, path, 0))
                    .collect(Collectors.toList());

        } catch (IOException e) {
            e.printStackTrace();
            return List.of();
        }
    }

    // Searches a file for words ending with a specific suffix
    // Adds results to a shared list up to a global limit
    // Uses synchronization and atomic flag for thread-safe early stopping
    private static void wordsEndingWithInFile(
            Path path,
            String suffix,
            List<LocatedWord> found,
            int limit,
            AtomicBoolean done) {

        try (BufferedReader reader = Files.newBufferedReader(path)) {

            String line;
            int lineNum = 0;

            while ((line = reader.readLine()) != null) {

				// If the limit is exceeded early exit
				// Also check if interrupted, then the thread should stop itself
                if (done.get() || Thread.currentThread().isInterrupted()) {
                    return;
                }

                lineNum++;

                for (String word : WordUtils.extractWords(line)) {

                    if (word.endsWith(suffix)) {

                        // Acquire lock to prevent race condition
                        // Add valid word if found.size() < limit
                        synchronized (found) {

                            if (found.size() < limit) {
                                found.add(new LocatedWord(word, path, lineNum));
                            }

                            // After adding do a final check on size
                            // If limit has been reached, exit
                            if (found.size() >= limit) {
                                done.set(true);
                                return;
                            }
                        }
                    }
                }
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

	private static void printHelp() {
    System.out.println("""
        Concurrent Text Search Tool

        Commands:

          help
            Show this help message

          shortestWord <directory>
            Find the shortest word in all .txt files

          consonants <directory> <n>
            Find a word with exactly n consonants

          allLines <directory>
            Find words that appear on every line of a file

          suffix <directory> <suffix> <limit>
            Find words ending with a suffix (up to limit results)
        """);
	}

	private static void requireArgs(String[] args, int expected, String message) {
		if (args.length != expected) {
			throw new IllegalArgumentException(message);
		}
	}
}