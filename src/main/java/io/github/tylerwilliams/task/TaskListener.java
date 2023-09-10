package io.github.tylerwilliams.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Listener which receives notifications of task lifecycle events.
 */
public interface TaskListener {

    /**
     * Called when a {@link Task} has been queued for execution.
     * @param task The task queued.
     */
    default void onTaskQueued(Task task) {}

    /**
     * Called when a {@link Task} has begun execution.
     * @param task The task started
     */
    default void onTaskStarted(Task task) {}

    /**
     * Called when a {@link Task} has completed either successfully or exceptionally.
     * @param task The task completed.
     * @param error The exception thrown during task execution. This value will be <code>null</code> if the task completed successfully.
     */
    default void onTaskCompleted(Task task, Throwable error) {}

    /**
     * @return A {@link TaskListener} which will forward each event to a list of other listeners.
     * Any exceptions thrown from the delegate listeners are caught and logged.
     */
    static TaskListener aggregateOf(Collection<TaskListener> delegateListeners) {
        Logger log = LoggerFactory.getLogger(TaskListener.class);

        Collection<TaskListener> delegatesSnapshot = Set.copyOf(delegateListeners);
        return new TaskListener() {
            @Override
            public void onTaskQueued(Task task) {
                eachDelegate(it -> it.onTaskQueued(task));
            }

            @Override
            public void onTaskStarted(Task task) {
                eachDelegate(it -> it.onTaskStarted(task));
            }

            @Override
            public void onTaskCompleted(Task task, Throwable error) {
                eachDelegate(it -> it.onTaskCompleted(task, error));
            }

            private void eachDelegate(Consumer<TaskListener> action) {
                for (var delegate : delegatesSnapshot) {
                    try {
                        action.accept(delegate);
                    } catch (Throwable e) {
                        log.error("Could not invoke task listener " + delegate, e);
                    }
                }
            }
        };
    }

}
