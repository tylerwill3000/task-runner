package io.github.tylerwilliams.task;

import java.util.List;

import static java.util.stream.Collectors.joining;

/**
 * Thrown when a cycle is detected in the list of tasks to be executed.
 */
public class TaskCycleException extends RuntimeException {
    private final List<Task> cycle;

    public TaskCycleException(List<Task> cycle) {
        super(createCycleMessage(cycle));
        this.cycle = List.copyOf(cycle);
    }

    public List<Task> getCycle() {
        return cycle;
    }

    private static String createCycleMessage(List<Task> cycle) {
        return "Task cycle detected:\n\n" + cycle.stream().map(Object::toString).collect(joining("\n"));
    }
}
