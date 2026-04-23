# Task Manager

A CLI-based task management application that stores tasks locally as JSON. Built as a learning project to demonstrate layered Java architecture, domain modelling, and test-driven development.

---

## Features

- **Task Management** — Create, update, and delete tasks directly from the terminal
- **Priority Levels** — Assign `LOW`, `MEDIUM`, `HIGH`, or `URGENT` priority to any task
- **Status Workflow** — Track tasks through `TODO → IN_PROGRESS → REVIEW → DONE`
- **Due Dates and Tags** — Organise tasks with deadlines and free-form labels
- **Filtering** — List tasks by status, priority, or overdue state
- **Statistics** — View task counts and completion summaries
- **Quick-Add Syntax** — Create structured tasks from a single natural string using `!priority`, `#date`, and `@tag` markers
- **Two-Way Sync** — Merge task lists from two sources with configurable conflict resolution

---

## Requirements

- Java 17 or higher
- Gradle (or use the included Gradle wrapper — no installation required)

---

## Installation

### Option 1 — Clone and run with Gradle wrapper

```bash
git clone https://github.com/your-org/task-manager.git
cd task-manager
./gradlew build
```

On Windows:

```bash
gradlew.bat build
```

### Option 2 — Run directly without building

```bash
./gradlew run --args="list"
```

### Verify installation

```bash
./gradlew run --args="--help"
```

---

## Usage

All commands are run via Gradle using `--args`:

```bash
./gradlew run --args="<command> [options]"
```

### Create a task

```bash
./gradlew run --args="add --title 'Fix login bug' --priority HIGH"
```

### Quick-add using natural syntax

```bash
./gradlew run --args="add 'Fix login bug !high #tomorrow @backend'"
```

Supported markers:

| Marker      | Example                               | Meaning                         |
| ----------- | ------------------------------------- | ------------------------------- |
| `!priority` | `!high`, `!1`–`!4`                    | Sets priority (1=LOW, 4=URGENT) |
| `#date`     | `#tomorrow`, `#friday`, `#2026-05-01` | Sets due date                   |
| `@tag`      | `@backend`                            | Adds a tag                      |

Priority markers can appear anywhere in the string — at the start, middle, or end:

```bash
# All of these are valid
./gradlew run --args="add 'Fix login bug !high #tomorrow'"
./gradlew run --args="add '!high Fix login bug #tomorrow'"
./gradlew run --args="add '#tomorrow Fix login bug !high'"
```

### List tasks

```bash
# List all tasks
./gradlew run --args="list"

# List only overdue tasks
./gradlew run --args="list -o"

# Filter by status
./gradlew run --args="list --status IN_PROGRESS"

# Filter by priority
./gradlew run --args="list --priority HIGH"
```

### Update a task

```bash
./gradlew run --args="update --id <task-id> --status DONE"
./gradlew run --args="update --id <task-id> --priority URGENT"
```

### Delete a task

```bash
./gradlew run --args="delete --id <task-id>"
```

### View statistics

```bash
./gradlew run --args="stats"
```

---

## Project Structure

```
TaskManager/
├── build.gradle.kts                    Gradle build config (Kotlin DSL)
├── gradlew / gradlew.bat               Gradle wrapper scripts
├── README.md
└── src/
    ├── main/java/za/co/wethinkcode/taskmanager/
    │   ├── app/        TaskManager.java          Business logic / service layer
    │   ├── cli/        TaskManagerCli.java       CLI entry point, argument parsing
    │   ├── model/      Task.java                 Core task entity
    │   │               TaskPriority.java         Priority enum (LOW→URGENT)
    │   │               TaskStatus.java           Status enum (TODO→DONE)
    │   ├── storage/    TaskStorage.java          JSON persistence (tasks.json)
    │   └── util/       TaskPriorityManager.java  Weighted priority scoring + sorting
    │                   TaskTextParser.java       Quick-add text parser
    │                   TaskMergeService.java     Two-way sync with conflict resolution
    └── test/java/za/co/wethinkcode/taskmanager/
        ├── app/        TaskManagerTest.java
        └── util/       TaskPriorityManagerTest.java
                        TaskTextParserTest.java
                        TaskMergeServiceTest.java
```

---

## Configuration

Task Manager stores all data in a `tasks.json` file created in the working directory when the application first runs.

| Setting           | Location                           | Default     |
| ----------------- | ---------------------------------- | ----------- |
| Task storage file | `./tasks.json` (working directory) | Hardcoded   |
| Conflict strategy | `TaskMergeService` constructor     | `TIMESTAMP` |

**Note:** The storage path is currently hardcoded. Running the application from different directories will create separate `tasks.json` files. This is a known limitation — see [Troubleshooting](#troubleshooting).

---

## Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "za.co.wethinkcode.taskmanager.util.TaskPriorityManagerTest"

# View test report
open build/reports/tests/test/index.html
```

---

## Troubleshooting

### Tasks not appearing after creation

Check which directory you ran the command from. The `tasks.json` file is created in the working directory — if you run from different directories, you will have separate task stores.

**Fix:** Always run commands from the project root directory.

```bash
cd /path/to/task-manager
./gradlew run --args="list"
```

### `JAVA_HOME` not set or wrong Java version

```bash
java -version   # Should show 17 or higher
```

If not, install Java 17 and ensure `JAVA_HOME` points to the correct installation.

### Build fails with Gradle errors

Try cleaning the build cache:

```bash
./gradlew clean build
```

### Invalid priority number in quick-add

Only priority values `1`–`4` and named values (`low`, `medium`, `high`, `urgent`) are supported. Invalid numbers like `!5` are silently ignored and the task defaults to `MEDIUM` priority.

```bash
# Valid
./gradlew run --args="add 'Fix bug !high'"
./gradlew run --args="add 'Fix bug !3'"

# Invalid — defaults to MEDIUM silently
./gradlew run --args="add 'Fix bug !5'"
```

### `tasks.json` corrupted or causing errors

Delete the file and restart — all tasks will be lost:

```bash
rm tasks.json
```

---

## Contributing

Contributions are welcome. Please follow these steps:

1. Fork the repository
2. Create a feature branch
   ```bash
   git checkout -b feature/your-feature-name
   ```
3. Write tests for your changes
4. Ensure all tests pass
   ```bash
   ./gradlew test
   ```
5. Commit with a clear message
   ```bash
   git commit -m "feat: add recurring task support"
   ```
6. Push and open a pull request

Please follow existing code conventions — layered architecture (CLI → App → Storage → Model), no cross-layer dependencies, and test coverage for all new business logic.

---

## License

This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

---

## Acknowledgements

- [Apache Commons CLI](https://commons.apache.org/proper/commons-cli/) — CLI argument parsing
- [Gson](https://github.com/google/gson) — JSON serialisation
- [JUnit Jupiter](https://junit.org/junit5/) — Testing framework
- [Mockito](https://site.mockito.org/) — Mocking framework
- [AssertJ](https://assertj.github.io/doc/) — Fluent assertions
