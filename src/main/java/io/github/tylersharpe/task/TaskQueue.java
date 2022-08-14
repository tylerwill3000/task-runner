package io.github.tylersharpe.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;

class TaskQueue {
    private final static Logger log = LoggerFactory.getLogger(TaskQueue.class);

    private final Map<Task, Set<Task>> remainingTasks;
    private final Set<Task> allUniqueTasksQueued;
    private final BlockingQueue<Task> currentQueuedTasks;
    private final TaskListener taskListener;

    TaskQueue(Collection<Task> tasks, TaskListener taskListener) {
        this.taskListener = taskListener;
        this.allUniqueTasksQueued = new HashSet<>();
        this.currentQueuedTasks = new LinkedBlockingQueue<>();
        this.remainingTasks = mapTasksToDependencies(tasks);

        queueRunnableTasks();
    }

    boolean hasRemainingTasks() {
        return !remainingTasks.isEmpty();
    }

    Task pollNextTask() throws InterruptedException {
        return currentQueuedTasks.poll(30, TimeUnit.MINUTES);
    }

    synchronized void markFinished(Task finishedTask, Throwable taskError) {
        taskListener.onTaskCompleted(finishedTask, taskError);

        if (remainingTasks.isEmpty()) {
            return;
        }

        if (taskError != null) {
            // task did not complete successfully; discard any tasks which depend on it
            Collection<Task> dependents = remainingTasks.entrySet()
                    .stream()
                    .filter(e -> e.getValue().contains(finishedTask))
                    .map(Map.Entry::getKey)
                    .toList();

            for (Task dependent : dependents) {
                log.debug("Discarding {} since {} failed", dependent, finishedTask);
                remainingTasks.remove(dependent);
            }
        } else {
            // task completed successfully; remove it from dependencies of other tasks
            remainingTasks.values().forEach(dependencies -> dependencies.remove(finishedTask));
            queueRunnableTasks();
        }
    }

    private synchronized void queueRunnableTasks() {
        Collection<Task> newTasksWithNoDependencies = remainingTasks.entrySet()
                .stream()
                .filter(dependencies -> dependencies.getValue().isEmpty())
                .map(Map.Entry::getKey)
                .filter(allUniqueTasksQueued::add)
                .toList();

        if (newTasksWithNoDependencies.isEmpty() && !remainingTasks.isEmpty()) {
            // should never get here as a task cycle exception is thrown if a cycle is found during construction
            throw new IllegalStateException("Cannot determine next task to execute as no tasks currently exist with no dependencies");
        }

        for (Task taskToQueue : newTasksWithNoDependencies) {
            log.debug("Queuing task {}", taskToQueue);
            remainingTasks.remove(taskToQueue);
            currentQueuedTasks.add(taskToQueue);
            taskListener.onTaskQueued(taskToQueue);
        }
    }

    private Map<Task, Set<Task>> mapTasksToDependencies(Collection<Task> tasks) {
        Set<Task> allUniqueTasks = new HashSet<>();
        for (Task task : tasks) {
            crawlTasks(task, allUniqueTasks, new LinkedList<>());
        }

        return allUniqueTasks.stream().collect(toMap(identity(), task -> new HashSet<>(task.getDependencies())));
    }

    private void crawlTasks(Task from, Set<Task> allUniqueTasks, Deque<Task> currentCrawlHistory) {
        if (currentCrawlHistory.contains(from)) {
            List<Task> cycle = new ArrayList<>(currentCrawlHistory);
            cycle.add(from);
            throw new TaskCycleException(cycle);
        }

        if (!allUniqueTasks.add(from)) {
            return;
        }

        currentCrawlHistory.addLast(from);
        for (Task dependsOn: from.getDependencies()) {
            crawlTasks(dependsOn, allUniqueTasks, currentCrawlHistory);
        }
        currentCrawlHistory.removeLast();
    }
}
