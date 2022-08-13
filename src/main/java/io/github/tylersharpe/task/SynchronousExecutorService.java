package io.github.tylersharpe.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

class SynchronousExecutorService implements ExecutorService {
    private static final Logger log = LoggerFactory.getLogger(SynchronousExecutorService.class);

    static final SynchronousExecutorService INSTANCE = new SynchronousExecutorService();

    @Override
    public void shutdown() {
    }

    @Override
    public List<Runnable> shutdownNow() {
        return List.of();
    }

    @Override
    public boolean isShutdown() {
        return false;
    }

    @Override
    public boolean isTerminated() {
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) {
        return true;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        try {
            T result = task.call();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Future<?> submit(Runnable task) {
        return submit(task, null);
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        task.run();
        return CompletableFuture.completedFuture(result);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) {
        return invokeAll(tasks, Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
        return tasks.stream().map(this::submit).toList();
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws ExecutionException {
        return invokeAny(tasks, Long.MAX_VALUE, TimeUnit.DAYS);
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws ExecutionException {
        for (var task : tasks) {
            try {
                return task.call();
            } catch (Exception e) {
                log.error("Error invoking task", e);
            }
        }

        throw new ExecutionException("No task completed successfully", null);
    }

    @Override
    public void execute(Runnable command) {
        command.run();
    }
}
