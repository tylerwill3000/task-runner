package io.github.tylersharpe.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

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

    private final ExecutorService taskExecutor;
    private final Collection<TaskListener> taskListeners = new ArrayList<>();

    /**
     * Creates a new task runner which will execute tasks in parallel using a thread pool of the specified size.
     * If a value of 1 is passed for the number of threads, this method behaves identically to calling {@link TaskRunner#newSynchronousRunner()}.
     */
    public static TaskRunner newParallelRunner(int numThreads) {
        if (numThreads <= 0) {
            throw new IllegalArgumentException("Number of threads must be >= 1");
        }

        return numThreads == 1 ? newSynchronousRunner() : new TaskRunner(Executors.newFixedThreadPool(numThreads));
    }

    /**
     * Creates a task runner which will execute tasks synchronously in the calling thread.
     */
    public static TaskRunner newSynchronousRunner() {
        return new TaskRunner(SynchronousExecutorService.INSTANCE);
    }

    TaskRunner(ExecutorService taskExecutor) {
        this.taskExecutor = taskExecutor;
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
        TaskQueue queue = new TaskQueue(tasks, combinedListener);
        ExecutorService taskEventsThreadPool = Executors.newCachedThreadPool();
        Thread pollThread = Thread.currentThread();

        List<Future<?>> taskFutures = new ArrayList<>(tasks.size());
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

            Future<?> taskFuture = taskExecutor.submit(() -> {
                Task nextTask = nextTaskRef.get();
                taskEventsThreadPool.submit(() -> combinedListener.onTaskStarted(nextTask));

                AtomicReference<Throwable> taskError = new AtomicReference<>();
                try {
                    log.info("Executing {}", nextTask);
                    nextTask.run();
                    log.info("Successfully executed {}", nextTask);
                } catch (Throwable e) {
                    log.error("Could not execute " + nextTask, e);
                    taskError.set(e);
                }

                taskEventsThreadPool.submit(() -> queue.markFinished(nextTask, taskError.get()));

                if (taskError.get() != null && !continueOnFail && !pollThread.isInterrupted()) {
                    pollThread.interrupt();
                }
            });
            taskFutures.add(taskFuture);
        }

        awaitAllFutures(taskFutures);
    }

    private void awaitAllFutures(Collection<Future<?>> futures) {
        for (var future : futures) {
            if (future.isDone()) {
                continue;
            }

            try {
                future.get();
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
