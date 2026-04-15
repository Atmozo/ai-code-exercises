```prompt
I'd like you to act as a senior developer who deeply understands the Task Manager codebase's domain model. I'm a junior developer trying to make sense of the business logic and domain concepts in this application.

Here's what I've found in the codebase:

// Task.java — core entity
public class Task {
    private String id;              // UUID
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime dueDate;
    private LocalDateTime completedAt;
    private List<String> tags;

    public void markAsDone() { ... }
    public boolean isOverdue() { ... }
    public void update(Task updates) { ... }
    public void addTag(String tag) { ... }
    public boolean removeTag(String tag) { ... }
}

// TaskPriority.java
public enum TaskPriority { LOW(1), MEDIUM(2), HIGH(3), URGENT(4); }

// TaskStatus.java
public enum TaskStatus { TODO("todo"), IN_PROGRESS("in_progress"), REVIEW("review"), DONE("done"); }

Based on this code, my current understanding is:
- The system seems to be modeling a task lifecycle — from creation through completion — with urgency and organizational metadata attached
- I think TaskPriority and TaskStatus are separate because urgency (how important) is independent of progress (how far along) — a LOW priority task can still be IN_PROGRESS
- The completedAt field appears to represent the exact moment a task was marked done, separate from updatedAt, possibly for reporting or SLA tracking
- I'm confused about why isOverdue() checks status != DONE instead of checking completedAt == null — aren't those the same thing?

Could you, as a senior developer:
1. Validate my current understanding, correcting any misconceptions
2. Help me recognize the core domain concepts represented in this code
3. Explain the relationships between these entities in business terms, not just technical terms
4. Clarify any domain-specific terminology or patterns I might be missing — particularly around task lifecycle and state transitions
5. Help me connect these models to actual user-facing features, specifically the list, stats, and status CLI commands

Then, please ask me 3-5 questions that test my understanding of this domain model — questions that make me think about the business logic, not just the code syntax.

Finally, suggest a simple diagram I could sketch to visualize the task lifecycle and entity relationships that would help solidify my understanding.
```

### Answer

2 DOMAIN ANALYSIS

1. Validating Your Understanding

You're thinking about this the right way. A few refinements:
Correct — you've identified the core concept well. This models a task lifecycle with two orthogonal dimensions: how urgent is it and where is it in the workflow. That separation is intentional and meaningful.
Correct — priority and status being independent is a deliberate domain decision. In real teams, urgency doesn't determine workflow position. A LOW priority task can sit IN_PROGRESS for weeks while an URGENT one jumps straight to DONE in an hour.
Correct on completedAt — it's a dedicated audit timestamp. updatedAt answers "when was anything last touched?" while completedAt answers "when was this specifically finished?" The getStatistics() method uses completedAt to count tasks completed in the last 7 days — you can't do that with updatedAt alone.
Your confusion about isOverdue() is the sharpest question you've asked. They are not the same thing. Here's why:
java// Current implementation
public boolean isOverdue() {
return this.dueDate.isBefore(LocalDateTime.now()) && this.status != TaskStatus.DONE;
}
completedAt == null would fail for any task that was never marked done through markAsDone() — for example, if someone manually called setStatus(DONE) directly without going through the proper method. The status != DONE check is more defensive. It captures the intent of the task being done regardless of how it got there. That said — this is a real design tension worth raising with the team.

2. Core Domain Concepts

Three concepts are doing the heavy lifting here:
Lifecycle — A task moves through states over time. Creation, progress, review, completion. The timestamps (createdAt, updatedAt, dueDate, completedAt) are the audit trail of that journey.
Urgency vs Progress — TaskPriority answers "how much does this matter?" TaskStatus answers "where is this right now?" These are independent axes. Conflating them is a common modeling mistake — this codebase correctly keeps them separate.
Overdue as a derived state — Overdue isn't stored anywhere. It's computed on the fly from dueDate and status. This is an important pattern: rather than maintaining a separate isOverdue boolean that could get out of sync, the system derives it from facts that are already true. Any time you see boolean isX() returning a computation rather than a field, that's the same idea.

3. Entity Relationships in Business Terms

There's really one entity here — Task — with two supporting classifiers:
TaskPriority is a ranking signal. It tells users and the system which tasks deserve attention first. The integer backing (LOW=1 through URGENT=4) means you could sort tasks by priority numerically, though the current codebase doesn't do that yet.
TaskStatus is a workflow position. It represents where in the team's process a task sits. The string backing ("todo", "in_progress") is significant — it's what gets stored in JSON, so changing these values would break existing saved data. That's a backward compatibility concern you'd need to flag if you ever renamed a status.
The relationship is: one Task has exactly one Priority and one Status at any point in time, but both can change independently over the task's life.

4. Domain Patterns You Might Be Missing

State machine — TaskStatus implies a state machine even though one isn't enforced in code. The intended flow is TODO → IN_PROGRESS → REVIEW → DONE, but nothing currently prevents jumping from TODO directly to DONE or even backwards from DONE to TODO. Whether that's intentional flexibility or an oversight is worth asking.
Partial update pattern — Task.update(Task updates) uses null-checks to apply only non-null fields from a dummy object. This is a manual implementation of what frameworks like JPA handle automatically. It works but is fragile — if you add a new field to Task, you must remember to add it to update() too or changes to that field will silently be ignored.
Tags as flat metadata — Tags are just List<String> with no structure. They're organizational labels, not relationships. This is intentionally simple but means there's no tag taxonomy, no validation, and no way to query "all tasks tagged X" from the CLI yet.

5. Connecting Models to CLI Features

CLI CommandDomain concept it exerciseslist -s in_progressFilters by workflow position (TaskStatus)list -p 3Filters by urgency (TaskPriority)list -oQueries derived overdue state (isOverdue())status <id> doneTriggers state transition, sets completedAt via markAsDone()statsAggregates across all lifecycle dimensions — status counts, priority counts, overdue count, recent completions
stats is the most domain-rich command. It essentially produces a snapshot of the entire task portfolio at a point in time.

3–5 Questions to Test Your Understanding
Think through these before looking at the code for answers:

If a task is marked DONE today but its dueDate was yesterday, is it overdue? What does the current code say, and does that match what you'd expect as a user?
What happens to updatedAt when you call markAsDone()? Is that the right behavior? What if you wanted to know the last time a task's description was changed, separately from when it was completed?
A task is currently IN_PROGRESS. Someone calls status <id> todo to reset it. What gets updated in the data? Does completedAt change? Should it?
If you added a fifth status called CANCELLED, what would break across the codebase? Think about isOverdue(), getStatistics(), the CLI formatter, and the JSON storage before answering.
Tags have no validation — you can add the same tag twice if you call addTag() carefully enough. Can you? Read addTag() and tell me whether that's true or false and why.
