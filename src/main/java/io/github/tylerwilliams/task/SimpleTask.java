package io.github.tylerwilliams.task;

import java.util.*;

/**
 * Basic {@link Task} implementation suitable for use in small and simple applications, tests, etc.
 */
public class SimpleTask implements Task {
    private final String name;
    private final Set<Task> dependencies = new HashSet<>();
    private final Runnable action;

    public SimpleTask(String name, Runnable action) {
        this.name = Objects.requireNonNull(name, "name");
        this.action = Objects.requireNonNull(action, "action");
    }

    public String getName() {
        return name;
    }

    @Override
    public Set<? extends Task> getDependencies() {
        return Set.copyOf(dependencies);
    }

    public void dependsOn(Task... tasks) {
        dependencies.addAll(List.of(tasks));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SimpleTask otherTask && Objects.equals(name, otherTask.name);
    }

    @Override
    public int hashCode() {
        return name.hashCode();
    }

    @Override
    public String toString() {
        return name + "(dependencies=" +
                dependencies.stream().map(task -> task instanceof SimpleTask simpleTask ? simpleTask.name : task).toList() +
                ")";
    }

    @Override
    public void run() {
        action.run();
    }
}
