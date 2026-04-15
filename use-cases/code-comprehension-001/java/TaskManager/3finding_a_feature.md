```prompt
I need to work on the overdue task detection and statistics reporting feature in the Task Manager codebase, but I'm not sure where all the relevant code lives.

My approach so far:
- I've searched for keywords like overdue, stats, isOverdue, completedAt
- I looked in the app/ and model/ directories since they seemed most likely to contain business logic
- I think the feature might relate to Task.java, TaskManager.java, and possibly the list -o CLI flag

Project structure:
TaskManager/
└── src/
    ├── main/java/za/co/wethinkcode/taskmanager/
    │   ├── app/        (TaskManager.java)
    │   ├── cli/        (TaskManagerCli.java)
    │   ├── model/      (Task.java, TaskPriority.java, TaskStatus.java)
    │   └── storage/    (TaskStorage.java)
    └── test/java/za/co/wethinkcode/taskmanager/
        └── app/        (TaskManagerTest.java)

Based on my search, these files might be relevant but I'm not sure how they all connect:
- Task.java — contains isOverdue() and completedAt field
- TaskManager.java — contains getStatistics() and listTasks() with a showOverdue flag
- TaskStorage.java — contains getOverdueTasks() which seems to do the actual filtering
- TaskManagerCli.java — contains handleListCommand() with -o flag and handleStatsCommand()

Can you help me:
1. Evaluate my search approach and suggest improvements
2. Identify which files and directories most likely contain the implementation for this feature, and in what order I should read them
3. Suggest specific search terms or patterns that would be more effective for finding all touch points of this feature
4. Explain what parts of the feature are located in different layers — model, storage, app, and CLI — and why
5. Recommend a step-by-step investigation process to trace the complete flow from CLI command to overdue result

Also, what questions should I ask myself as I explore the code to confirm I've found all the relevant parts? What specific patterns should I look for to know I haven't missed a touch point?

After your guidance, could you give me a small challenge to test my understanding of how to navigate this feature's code?


```

### Answer

1. Evaluating Your Search Approach
   Your instincts are good — you found all four relevant files on your first pass, which means your keyword choices were solid. Two refinements worth internalizing for future searches:
   What you did well: Searching by method name (isOverdue, getStatistics) rather than just concept words (overdue, stats) is the right habit. Method names are exact strings that appear in exactly one definition and potentially many call sites — that contrast tells you about coupling.
   What to add next time: Once you have candidate files, search for callers not just definitions. You found getOverdueTasks() in TaskStorage but the more important question is: who calls it? Searching for getOverdueTasks across the whole project would immediately show you that TaskManager.listTasks() is the only caller — which tells you the feature has exactly one entry path.
   One gap: You didn't mention checking TaskManagerTest.java. Test files are often the fastest way to understand a feature's intended behavior — they describe what the feature should do in plain method names before you read any implementation.

2. Files in Order of Reading
   Read them in this sequence — each one gives you context that makes the next easier:
   First — Task.java (model layer)
   This is where the core logic lives. isOverdue() is the single source of truth for what "overdue" means in this system. Understand this before anything else.
   Second — TaskStorage.java (storage layer)
   getOverdueTasks() calls isOverdue() on every task and filters. It's a thin wrapper — once you've read isOverdue() this takes thirty seconds.
   Third — TaskManager.java (app layer)
   Two methods matter: listTasks() which delegates to getOverdueTasks() when showOverdue is true, and getStatistics() which calls isOverdue() directly via a stream to produce the overdue count. Notice these are two different code paths to the same concept.
   Fourth — TaskManagerCli.java (CLI layer)
   Now you can read handleListCommand() and handleStatsCommand() knowing exactly what they're triggering underneath.
   Fifth — TaskManagerTest.java
   Read the tests last to verify your mental model matches the intended behavior.

3. More Effective Search Patterns
   Beyond what you already searched, these would find remaining touch points:
   Search termWhat it findsgetOverdueTasksEvery caller of the storage methodshowOverdueWhere the boolean flag is set and passedcompletedLastWeekThe stats map key — finds both producer and consumersevenDaysAgoThe specific stats logic in getStatistics()isAfterDate comparison logic anywhere in the codebase-oThe CLI flag definition and its handler
   The pattern to internalize: search for the data (field names, map keys, variable names) as well as the behavior (method names). Data searches find places the feature touches that behavior searches miss.

4. What Lives in Each Layer and Why
   This feature is split across all four layers deliberately:
   Model (Task.java) — owns the definition of overdue. isOverdue() encapsulates the business rule: past due date AND not done. This lives here because the rule belongs to the entity itself, not to any infrastructure concern.
   Storage (TaskStorage.java) — owns the query. getOverdueTasks() applies isOverdue() across all tasks. Storage's job is retrieval and filtering — it doesn't decide what overdue means, it just asks each task.
   App (TaskManager.java) — owns the orchestration. listTasks() decides when to invoke the overdue query. getStatistics() independently uses isOverdue() to count — note this bypasses getOverdueTasks() entirely and calls the model directly via a stream. That's a subtle duplication worth noting.
   CLI (TaskManagerCli.java) — owns the interface. It translates the -o flag into showOverdue=true and passes it down. It also formats and prints whatever comes back. It knows nothing about how overdue is determined.

5. Step-by-Step Investigation Process
   Use this process for any feature, not just this one:
   Step 1 — Find the user-facing entry point. Start at the CLI. For this feature: handleListCommand() parsing -o, and handleStatsCommand().
   Step 2 — Follow the call down one layer. handleListCommand() calls taskManager.listTasks(null, null, true). Note the true — that's showOverdue.
   Step 3 — Trace through the app layer. listTasks() checks if (showOverdue) and calls getStorage().getOverdueTasks(). One branch, one caller.
   Step 4 — Hit the storage layer. getOverdueTasks() streams all tasks and calls task.isOverdue() on each. Now you're at the bottom of the call chain.
   Step 5 — Read the model logic. isOverdue() in Task.java is where the feature actually lives. Two conditions: dueDate.isBefore(now) AND status != DONE.
   Step 6 — Check for alternate paths. Search for other callers of isOverdue(). You'll find getStatistics() in TaskManager uses it independently. That's a second path to the same logic that didn't go through getOverdueTasks() — worth flagging.
   Step 7 — Read the tests. Confirm your understanding matches what was intended.

Questions to Ask Yourself While Exploring

"Where is this method called from?" — if only one place, the feature is tightly scoped; if many, changes will ripple.
"Is the logic defined here or delegated?" — find where the actual decision is made, not just where it's triggered.
"Are there two paths to the same behavior?" — duplication in features means changes need to happen in multiple places.
"What happens if the input is null or edge-case?" — for this feature: what if dueDate is null? Read isOverdue() and check.
"Does the test cover this specific scenario?" — if not, it's a gap you should flag or fill.

Your Challenge
Without running the code, answer these three questions using only the source files:
Question 1 — A task has dueDate = yesterday, status = DONE, and completedAt = this morning. Does list -o show it? Trace through every layer to justify your answer.
Question 2 — getStatistics() counts overdue tasks and also counts tasks completed in the last 7 days. These are two separate streams over the same task list. Could one stream do both jobs? What would you need to change, and what's the tradeoff?
Question 3 — If you added a new status CANCELLED and wanted cancelled tasks to never appear as overdue, exactly which line in which file would you change, and what would the new line look like?
If you can answer all three by reading the code rather than running it, you understand this feature's implementation thoroughly.
