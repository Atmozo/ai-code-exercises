```prompt
I'm a junior developer who just joined this project. I've read the README but still need help understanding the project structure and technology stack.

Here's my current understanding of the project:

- It seems to be a CLI-based task management application that stores tasks locally
- It appears to use Java with Gradle as the build tool, Apache Commons CLI for argument parsing, and Gson for JSON serialization
- The folder structure seems to follow a standard Gradle layout (src/main/java, src/test/java) with packages organized by responsibility (cli, app, model, storage)

Project structure:
TaskManager/
├── build.gradle.kts
├── gradlew / gradlew.bat
├── README.md
└── src/
├── main/java/za/co/wethinkcode/taskmanager/
│ ├── app/ (TaskManager.java)
│ ├── cli/ (TaskManagerCli.java)
│ ├── model/ (Task.java, TaskPriority.java, TaskStatus.java)
│ └── storage/ (TaskStorage.java)
└── test/java/za/co/wethinkcode/taskmanager/
└── app/ (TaskManagerTest.java)

Key configuration files:
// build.gradle.kts
plugins { id("java"); id("application") }
application { mainClass.set("za.co.wethinkcode.taskmanager.cli.TaskManagerCli") }

dependencies {
implementation("commons-cli:commons-cli:1.9.0")
implementation("com.google.code.gson:gson:2.11.0")
testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
testImplementation("org.mockito:mockito-core:5.8.0")
testImplementation("org.assertj:assertj-core:3.24.2")
}

Could you:

1. Validate my understanding and correct any misconceptions
2. Identify additional key technologies, frameworks, and libraries used
3. Explain what each main folder likely contains and its purpose
4. Point out where the application entry points are located
5. Suggest 3-5 specific questions I should ask my team to deepen my understanding

I'm particularly confused about:

- Why tasks.json is hardcoded as the storage path instead of being configurable
- Why a temporary Task("tempTitle") object is created just to carry field updates in TaskManager
- Why there's only one test file and it only covers the app layer

After your explanation, could you suggest a small exploration exercise I could do to verify my understanding of the project structure?

```

### answer

1. Validating Your Understanding
   Your understanding is solid for a first read. A few refinements:

Correct — it's a CLI task manager with local JSON persistence.

Correct on the tech stack, but add one nuance: Gson doesn't handle Java's LocalDateTime natively, so TaskStorage includes custom serializer/deserializer adapters to handle date fields. That's a detail that will matter when you touch anything date-related.
Correct on the folder pattern — it's called layered architecture, where each package has one clear responsibility and dependencies only flow in one direction: cli → app → storage → model.

2. Additional Technologies to Note

Everything you listed is right. The one addition worth calling out explicitly is JUnit Platform Launcher (testRuntimeOnly), which is the engine that actually executes JUnit 5 tests at runtime — separate from the API you write tests against.

3. What Each Folder Contains and Why

PackageFile(s)PurposemodelTask, TaskPriority, TaskStatusPure data. No dependencies on anything else in the project. The foundation everything else is built on.storageTaskStorageReads and writes tasks.json. Holds tasks in a HashMap<String, Task>. Knows nothing about business rules.appTaskManagerBusiness logic and orchestration. Validates inputs, delegates to storage, computes statistics. The middleman.cliTaskManagerCliEntry point. Parses terminal arguments, calls TaskManager, prints results. Knows nothing about storage or JSON.
The key insight: each layer only talks to the layer directly below it. cli never touches TaskStorage directly. That separation is intentional and worth preserving when you add features.

4. Entry Points

There are two:
Runtime — declared in build.gradle.kts:
mainClass.set("za.co.wethinkcode.taskmanager.cli.TaskManagerCli")
TaskManagerCli.main(String[] args) is where execution begins every time you run ./gradlew run.
Tests — TaskManagerTest.java in src/test/.../app/. It's the only test file currently and it targets the app layer.

5. Questions to Ask Your Team

"Was hardcoding tasks.json intentional, or is making it configurable on the roadmap?" — This affects multi-user scenarios and makes automated testing write to disk, which is usually undesirable.
"Is the Task("tempTitle") update pattern a deliberate design choice?" — Creating a dummy task just to carry one updated field is unusual. It may be a quick solution that was never revisited, or there may be a reason for it.
"Are we planning to add tests for the storage and model layers?" — Understanding the team's testing philosophy early will save you from submitting PRs with the wrong test coverage expectations.
"Is filter-by-tag on the list command a known gap?" — Tags can be added and removed but there's no -t filter on list. Either it's planned or it was overlooked.
"Is there a contribution guide or PR checklist?" — Knowing how the team reviews code is more useful day-to-day than almost anything else.

Addressing Your Specific Confusions

Why is tasks.json hardcoded?

It's set directly in TaskManagerCli when it instantiates TaskManager("tasks.json"). This is likely a simplicity decision for a small tool, but it means tests that hit TaskStorage will create a real file on disk. A better pattern would be to pass the path in as an argument or environment variable.
Why the Task("tempTitle") pattern?
TaskManager.updateTaskPriority() (and updateTaskDueDate()) creates a throwaway Task object with a dummy title, sets only the field being updated on it, then passes it to storage.updateTask(). The update() method on Task uses null-checks to only apply non-null fields. It works, but it's awkward — the dummy title is meaningless and could confuse someone reading it cold.
Why only one test file covering only app?
The model layer is simple enough that some teams skip testing it directly. But TaskStorage doing real file I/O with no tests is a genuine gap — any change there is unverified. It's worth raising with your team.

Exploration Exercise

Do these four steps in order:
Step 1 — Create two tasks with different priorities:
bash./gradlew run --args="create 'Write tests' 'Cover storage layer' 2 2026-06-01 testing"
./gradlew run --args="create 'Fix bug' 'Null pointer in stats' 3 2026-05-01 bugs"

Step 2 — Open tasks.json and map every JSON field back to a field in Task.java. Find createdAt, status, priority, and tags in both places.

Step 3 — Run ./gradlew run --args="list -p 3" and confirm only the high-priority task appears. Then trace the -p flag through the code manually: handleListCommand() → TaskManager.listTasks() → TaskStorage.getTasksByPriority().

Step 4 — Run ./gradlew run --args="stats" and verify the numbers match what you created. Read through TaskManager.getStatistics() and make sure you understand every line.

If you can complete all four and explain what happened at each layer, you have a genuine working understanding of the full stack — not just the surface.
