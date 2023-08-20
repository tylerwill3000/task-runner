package io.github.tylersharpe.task;

import java.util.Set;

/**
 * A runnable unit of work which has dependencies on other tasks.
 */
public interface Task extends Runnable {

    /**
     * @return The set of tasks which must be executed before this task may run.
     */
    Set<? extends Task> getDependencies();

}
