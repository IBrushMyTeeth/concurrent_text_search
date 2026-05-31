# Concurrent Text Search Engine (Java)

A high-performance concurrent text processing engine built in Java, designed to explore and benchmark different concurrency models for large-scale file analysis.

The system recursively processes directories of text files and performs several analytical tasks such as:
- Finding the shortest word across a dataset
- Locating words with specific consonant counts
- Identifying words common to all lines in a file
- Searching for words matching suffix constraints with early termination

The project demonstrates practical use of:
- Java Executors and Work-Stealing Pools
- Virtual Threads
- CompletionService for task coordination
- Parallel Streams with ForkJoinPool
- BreakIterator for robust word segmentation
- Fine-grained cancellation and early-exit strategies

This project was originally developed as part of an academic assignment and later refactored into a standalone exploration of concurrent programming patterns in Java.

## Design Goals

This project was built to explore how different concurrency models in Java behave under realistic file-processing workloads.

It intentionally combines multiple strategies:

- **Work-stealing thread pools** for CPU-bound aggregation tasks
- **Virtual threads** for IO-heavy file traversal and early-exit workloads
- **CompletionService** for responsive completion-order result aggregation
- **Parallel streams** for intra-file parallelism
- **Atomic flags + synchronized blocks** for cancellation and shared state coordination

The goal is not only correctness, but also responsiveness and throughput under large directory trees.

## Architecture Overview

The application uses a shared concurrent processing pipeline based on
**file-level** task decomposition.

Files are discovered recursively and processed independently in parallel.
Each task performs line-oriented computation locally within a single file,
minimizing shared mutable state and synchronization overhead.

Results are coordinated through either:
- CompletionService for completion-order aggregation and early termination
- Parallel streams via the ForkJoinPool
- Shared cancellation signals using AtomicBoolean

Different workloads use different executor strategies depending on whether
the dominant cost is CPU-bound computation or I/O-bound file traversal.

## Running the program

Compile the files and run: java ConcurrentTextSearch help