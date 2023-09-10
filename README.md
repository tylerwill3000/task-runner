# Overview

This is a toy project which implements a task runner. A task runner is a service capable of executing a list of tasks in the proper order according to their dependencies on other tasks. The project is inspired by build systems like gradle and maven, which solve a similar problem.

# Features
- Fails fast when a task cycle is detected
- Ability to execute tasks serially or in parallel
- Ability to continue executing available tasks upon a single task failure

# Usage

See [TaskRunnerSpec](src/test/groovy/io/github/tylerwilliams/task/TaskRunnerSpec.groovy) for usage examples.
