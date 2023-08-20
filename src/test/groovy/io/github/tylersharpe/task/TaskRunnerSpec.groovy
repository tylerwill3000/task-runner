package io.github.tylersharpe.task

import groovy.transform.CompileStatic
import spock.lang.Specification

class TaskRunnerSpec extends Specification {

    def 'execute() will run all tasks in the proper order'() {
        given:
            SimpleTask a = new SimpleTask('A', () -> println('Running task A'))
            SimpleTask b = new SimpleTask('B', () -> println('Running task B'))
            SimpleTask c = new SimpleTask('C', () -> println('Running task C'))
            SimpleTask d = new SimpleTask('D', () -> println('Running task D'))
            SimpleTask e = new SimpleTask('E', () -> println('Running task E'))

            a.dependsOn(b, c, d, e)
            b.dependsOn(d)
            c.dependsOn(d, e)
            d.dependsOn(e)

            QueueOrderListener listener = new QueueOrderListener()
            runner.addListener(listener)

        when:
            runner.execute([b, a, e, c, d])

        then:
            listener.queueOrder[0] == e
            listener.queueOrder[1] == d
            listener.queueOrder[2..3] ==~ [b, c]
            listener.queueOrder[4] == a

        where:
            runner << [
                TaskRunner.newSynchronousRunner(),
                TaskRunner.newParallelRunner(2)
            ]
    }

    def 'task execution ends immediately when a task fails and the runner is not configured to continue on failure'() {
        given:
            SimpleTask a = new SimpleTask('A', () -> println('Running task A'))
            SimpleTask b = new SimpleTask('B', () -> { throw new RuntimeException('Task B failed') })
            a.dependsOn(b)

            TaskRunner runner = TaskRunner.newSynchronousRunner()
            QueueOrderListener listener = new QueueOrderListener()
            runner.addListener(listener)

        when:
            runner.execute([a, b])

        then:
            listener.queueOrder == [b]
    }

    def 'task execution continues if a task fails but the runner is configured to continue'() {
        given:
            SimpleTask a = new SimpleTask('A', () -> println('Running task A'))
            SimpleTask b = new SimpleTask('B', () -> { throw new RuntimeException('Task B failed') })
            SimpleTask c = new SimpleTask('C', () -> println('Running task C'))

            a.dependsOn(b)

            TaskRunner runner = TaskRunner.newSynchronousRunner()
            QueueOrderListener listener = new QueueOrderListener()
            runner.addListener(listener)

        when:
            runner.execute([a, b, c], true)

        then:
            listener.queueOrder == [b, c]
    }

    def 'an exception is thrown if any task cycles exist'() {
        given:
            SimpleTask a = new SimpleTask('A', () -> println('Running task A'))
            SimpleTask b = new SimpleTask('B', () -> println('Running task B'))
            SimpleTask c = new SimpleTask('C', () -> println('Running task C'))

            a.dependsOn(b)
            b.dependsOn(c)
            c.dependsOn(a)

            TaskRunner runner = TaskRunner.newSynchronousRunner()

        when:
            runner.execute([a, b, c])

        then:
            TaskCycleException cycleException = thrown(TaskCycleException)
            cycleException.cycle == [a, b, c, a]
    }

    @CompileStatic
    class QueueOrderListener implements TaskListener {
        List<Task> queueOrder = []

        @Override
        void onTaskQueued(Task task) {
            queueOrder.add(task)
        }
    }
}
