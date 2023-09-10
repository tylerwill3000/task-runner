package io.github.tylerwilliams.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * <p>
 *     Executes a collection of {@link Task}s in the proper order according to their dependencies.
 * </p>
 * <p>
 *     Tasks can be executed synchronously or in parallel depending on the {@link ExecutorService} supplied at construction.
 * </p>
 */
public class TaskRunner {
    private static final Logger log = LoggerFactory.getLogger(TaskRunner.class);

    private final Supplier<ExecutorService> taskExecutorSupplier;
    private final Collection<TaskListener> taskListeners = new ArrayList<>();

    /**
     * Creates a new task runner which will execute tasks in parallel using a thread pool of the specified size.
     * If a value of 1 is passed for the number of threads, this method behaves identically to calling {@link TaskRunner#newSynchronousRunner()}.
     */
    public static TaskRunner newParallelRunner(int numThreads) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be >= 1");
        }

        return numThreads == 1
            ? newSynchronousRunner()
            : new TaskRunner(() -> Executors.newFixedThreadPool(numThreads));
    }

    /**
     * Creates a task runner which will execute tasks synchronously in the calling thread.
     */
    public static TaskRunner newSynchronousRunner() {
        return new TaskRunner(() -> SynchronousExecutorService.INSTANCE);
    }

    private TaskRunner(Supplier<ExecutorService> taskExecutorSupplier) {
        this.taskExecutorSupplier = taskExecutorSupplier;
    }

    public void addListener(TaskListener listener) {
        taskListeners.add(listener);
    }

    public void execute(Collection<Task> tasks) {
        execute(tasks, false);
    }

    /**
     * Executes a collection of tasks.
     * When this method returns, all tasks will either have completed (exceptionally or normally), or been discarded if a dependent task failed
     * @param tasks The tasks to execute.
     * @param continueOnFail Whether to continue execution if a task fails.
     *                       If <code>true</code>, any remaining tasks which don't depend on the failed task will be executed, and any remaining tasks which do depend on the failed task will be skipped.
     *                       If <code>false</code>, execution stops immediately upon the first task failure.
     * @throws TaskCycleException If a cycle is found in the collection of tasks to execute.
     */
    public void execute(Collection<Task> tasks, boolean continueOnFail) {
        Objects.requireNonNull(tasks, "tasks");
        if (tasks.isEmpty()) {
            throw new IllegalArgumentException("Tasks may not be empty");
        }
        
        TaskListener combinedListener = TaskListener.aggregateOf(taskListeners);
        io.github.tylerwilliams.task.TaskQueue queue = new io.github.tylerwilliams.task.TaskQueue(tasks, combinedListener);
        ExecutorService taskEventsExecutor = Executors.newCachedThreadPool();
        ExecutorService taskExecutor = taskExecutorSupplier.get();
        Thread pollThread = Thread.currentThread();

        while (queue.hasRemainingTasks()) {
            if (pollThread.isInterrupted()) {
                log.warn("Prematurely ending task execution due to task failure");
                break;
            }

            AtomicReference<Task> nextTaskRef = new AtomicReference<>();
            try {
                nextTaskRef.set(queue.pollNextTask());
            } catch (InterruptedException e) {
                log.warn("Prematurely ending task execution due to task failure");
                break;
            }

            taskExecutor.submit(() -> {
                Task nextTask = nextTaskRef.get();
                taskEventsExecutor.submit(() -> combinedListener.onTaskStarted(nextTask));

                AtomicReference<Throwable> taskError = new AtomicReference<>();
                try {
                    log.info("Executing {}", nextTask);
                    nextTask.run();
                    log.info("Successfully executed {}", nextTask);
                } catch (Throwable e) {
                    log.error("Could not execute " + nextTask, e);
                    taskError.set(e);
                }

                taskEventsExecutor.submit(() -> queue.markFinished(nextTask, taskError.get()));

                if (taskError.get() != null && !continueOnFail && !pollThread.isInterrupted()) {
                    pollThread.interrupt();
                }
            });
        }

        taskExecutor.shutdown();
        try {
            if (!taskExecutor.awaitTermination(1, TimeUnit.HOURS)) {
                throw new RuntimeException("Could not await termination of all tasks");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
