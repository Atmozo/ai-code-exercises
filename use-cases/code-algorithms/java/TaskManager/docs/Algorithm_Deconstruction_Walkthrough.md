# Algorithm Deconstruction Walkthrough

## Task Manager — Java Codebase

---

## Overview

This document records the full walkthrough of three algorithms from the Task Manager codebase. For each algorithm, three structured AI prompts were used to build understanding from the outside in — purpose, intent, and control flow — followed by implementation of fixes and tests.

---

## How to Use These Prompts

Each prompt is designed to be submitted to an AI assistant with the relevant code pasted in. Replace `[paste code here]` with the actual function or class. Answer the questions yourself first before reading the AI response — the act of forming your own answer is where the learning happens.

---

## Algorithm 1: Task Priority Sorting

**File:** `src/main/java/za/co/wethinkcode/taskmanager/util/TaskPriorityManager.java`  
**Methods:** `calculateTaskScore()`, `sortTasksByImportance()`, `getTopPriorityTasks()`

---

### Prompt 1 — Deconstruct the Algorithm

```
I'm trying to understand `calculateTaskScore` and `sortTasksByImportance`
in our Java codebase (TaskPriorityManager.java).

My current understanding:
1. I think this function calculates a numeric score per task, then sorts
   descending — highest score = highest priority to work on
2. Inputs are a List<Task> and it returns a sorted List<Task>.
   calculateTaskScore takes a single Task and returns an int
3. I'm confused about why the magic numbers (30, 20, 15, 10, 50, 15, 8, 5)
   were chosen — are they arbitrary or derived from some business rule?

Help me understand this by:
1. Breaking calculateTaskScore into its distinct scoring sections and
   explaining what business concern each one addresses
2. Walking through a concrete example: a task with priority=HIGH, due
   tomorrow, status IN_PROGRESS, tag "blocker", updated 2 hours ago
3. Explaining the core technique — this looks like a weighted scoring
   system. Is there a formal name for this pattern?
4. Pointing out any non-obvious design choices — specifically why DONE
   gets -50 but REVIEW only gets -15, and whether that gap is meaningful

After your explanation, ask me 2–3 questions testing my understanding of
the scoring weights, what happens when dueDate is null, and whether
sortTasksByImportance computes scores efficiently.
```

---

### Prompt 2 — Clarify Intent Behind the Code

```
I'm looking at calculateTaskScore in TaskPriorityManager.java. The
variable names are clear but the intent behind the weight values isn't
documented.

Context:
- This method is called by sortTasksByImportance, which is called by
  getTopPriorityTasks
- It manipulates a numeric score built up from multiple task fields
- The variable names suggest it's ranking tasks — but I don't know if
  the weights were tuned empirically or guessed

Help me by:
1. Suggesting what the constants (10, 30, 20, 15, 10, 50, 15, 8, 5)
   represent in business terms — what does each "buy" in rank position?
2. Identifying the design pattern: this resembles a decision matrix or
   priority queue heuristic — which is it, and what's the difference?
3. Writing pseudocode that strips away Java syntax and reveals just the
   decision logic
4. Drafting a Javadoc comment for calculateTaskScore that would make the
   intent clear to a future developer

Then give me 3–4 questions I should ask myself to validate whether my
understanding of the weights is correct — things I could check by looking
at how getTopPriorityTasks is called in the CLI layer.

Also suggest 2 safe experiments I could run (e.g. unit test inputs) to
verify the scoring behaviour without touching production data.
```

---

### Prompt 3 — Untangle the Control Flow

```
I'm tracing the conditional logic inside calculateTaskScore. Here's my
current understanding of the flow:
- It starts with a base score from priority
- Then enters an if/else-if chain on daysUntilDue
- Then two separate if/else-if blocks for status and tags
- Then a final if for recency

I'm unsure about:
1. Whether the due date block and status block are truly independent —
   can both affect score simultaneously, or does one short-circuit the other?
2. What happens when dueDate is null — does the null check fully protect
   the ChronoUnit.DAYS.between(...) call?
3. Whether a DONE task with tag "blocker" updated today could ever
   outscore an active task

Help me by:
1. Drawing the control flow as a text decision tree — every branch,
   every condition
2. Refactoring calculateTaskScore into named private methods (one per
   scoring section) without changing behaviour
3. Identifying any logic gaps — what task states aren't handled, and
   what score do they silently receive?

Then walk me through these 3 scenarios and ask me to predict the score
before revealing the answer:
- Task: priority=URGENT, dueDate=null, status=DONE, tags=[], updatedAt=yesterday
- Task: priority=LOW, dueDate=today, status=TODO, tags=["blocker"], updatedAt=1 hour ago
- Task: priority=MEDIUM, dueDate=3 days ago, status=REVIEW, tags=[], updatedAt=last week
```

---

### Fix Implemented

**Problem:** `sortTasksByImportance` called `calculateTaskScore` multiple times per task during sort comparisons — O(n log n) redundant calculations.

**Fix:** Pre-compute scores into a `HashMap` before sorting.

```java
public static List<Task> sortTasksByImportance(List<Task> tasks) {
    // Pre-compute scores once per task
    Map<Task, Integer> scoreCache = new HashMap<>();
    for (Task task : tasks) {
        scoreCache.put(task, calculateTaskScore(task));
    }
    return tasks.stream()
        .sorted(Comparator.comparing(scoreCache::get).reversed())
        .collect(Collectors.toList());
}
```

**Test result:** 7/7 passing

---

### Key Insights

- The algorithm is a **weighted scoring model** (multi-criteria decision analysis)
- All scoring sections are independent — they don't short-circuit each other
- The `-50` DONE penalty is large enough to guarantee no DONE task can outscore any active task regardless of bonuses (max bonus = 43)
- `sortTasksByImportance` in the original called `calculateTaskScore` ~32 times for 6 tasks during sort; the fix reduces this to exactly 6 calls

---

---

## Algorithm 2: Task Text Parser

**File:** `src/main/java/za/co/wethinkcode/taskmanager/util/TaskTextParser.java`  
**Methods:** `parseTaskFromText()`, `getNextWeekday()`

---

### Prompt 1 — Deconstruct the Algorithm

```
I'm trying to understand parseTaskFromText in TaskTextParser.java.

My current understanding:
1. I think this function converts a free-form string like
   "Fix bug !high #friday @backend" into a structured Task object
2. Inputs are a plain String, it returns a Task
3. I'm confused about why the algorithm reads from `text` (original)
   but mutates `title` (a copy) — why not just modify one string?

Help me understand this by:
1. Breaking parseTaskFromText into its distinct processing sections —
   what does each section do to the input string and what does it extract?
2. Walking through this concrete input step by step, showing what title,
   priority, tags, and dueDate look like after each section runs:
   "Fix login bug !high #friday @backend"
3. Explaining the core technique — what category of algorithm is this,
   and where else would you encounter this pattern in real software?
4. Explaining the non-obvious design choice of reading from `text` but
   mutating `title` — what bug does that prevent?

After your explanation, ask me 2–3 questions testing my understanding of
the processing order, what happens with multiple date markers, and whether
Pattern.compile is called efficiently.
```

---

### Prompt 2 — Clarify Intent Behind the Code

```
I'm looking at parseTaskFromText and getNextWeekday in TaskTextParser.java.

Context:
- parseTaskFromText is the public entry point, called from the CLI layer
- getNextWeekday is a private helper called only during date resolution
- The regex patterns are compiled inside the method on every call

Help me by:
1. Explaining the three distinct components of the priority regex
   \s!([1-4]|urgent|high|medium|low)\b — what does each part enforce,
   and what valid user input would fail to match because of one specific part?
2. Explaining getNextWeekday in plain English — what problem is it
   solving and what does the if (daysToAdd == 0) line specifically handle?
3. Writing pseudocode for parseTaskFromText that a non-programmer could
   read — no regex syntax, no Java, just the decision steps
4. Writing a Javadoc comment for getNextWeekday that explains what it
   does, what the edge case is, and what the two parameters represent
   (maximum 6 lines)

Then give me 3–4 questions I should ask myself to validate my
understanding, and suggest 2 safe experiments I could run using unit
test inputs to verify the parsing behaviour.
```

---

### Prompt 3 — Untangle the Control Flow

```
I'm tracing the full logic of parseTaskFromText including edge cases.

My current understanding of the control flow:
- Priority extracted first, then tags, then dates
- Markers removed from title as each section runs
- Date loop tries each marker in order and stops at first valid match

I'm unsure about:
1. What happens when a priority marker appears at the very start of the
   string with no preceding space — does it match?
2. What happens when multiple date markers exist — which one wins?
3. What happens when a date marker like #someday doesn't match anything
   — does it stay in the title or get removed silently?

Help me by:
1. Drawing the full control flow as a text decision tree — every branch,
   including unrecognised tokens and null cases
2. Identifying at least 3 silent failure modes — inputs that look valid
   to a user but produce unexpected results with no error
3. Predicting the exact output for each of these inputs section by section:
   - "!urgent Fix login bug #friday @backend"
   - "Fix login bug !high #next_week #friday @backend"
   - "Fix login bug !5 #tomorrow @backend"
4. For input B with two date markers — which wins and why? Does that
   match likely user intent? What would need to change to let the user
   control which date wins?
```

---

### Fixes Implemented

**Fix 1 — Priority at start of string ignored**

Original regex required a leading space, so `"!urgent Fix bug"` silently defaulted to MEDIUM.

```java
// Before
Pattern.compile("\\s!([1-4]|urgent|high|medium|low)\\b", ...)
// group(1)

// After
private static final Pattern PRIORITY_PATTERN = Pattern.compile(
    "(^|\\s)!([1-4]|urgent|high|medium|low)\\b",
    Pattern.CASE_INSENSITIVE
);
// group(2)
// Use compiled constant in replaceAll:
title = PRIORITY_PATTERN.matcher(title).replaceAll("");
```

**Fix 2 — Invalid priority number left in title**

`!5` didn't match the pattern but also wasn't removed from the title.

```java
// Remove unrecognised numeric priority markers (e.g. !5, !9)
title = title.replaceAll("(^|\\s)![5-9]\\b", "");
```

**Fix 3 — Static pattern constants**

Patterns were recompiled on every method call.

```java
private static final Pattern PRIORITY_PATTERN = Pattern.compile(...);
private static final Pattern TAG_PATTERN = Pattern.compile("\\s@(\\w+)");
private static final Pattern DATE_PATTERN = Pattern.compile("\\s#(\\w+)");
```

**Test result:** 16/16 passing

---

### Key Insights

- This is a **token extraction / mini DSL parser** — same pattern used in chat commands, calendar apps, search query interpreters
- The algorithm reads from `text` (immutable original) and writes to `title` (mutable copy) to prevent regex position shifts caused by earlier removals
- `getNextWeekday` uses modulo arithmetic to handle week wrapping; the `if (daysToAdd == 0)` guard ensures today's weekday always resolves to next week, not today
- Four silent failure modes exist: priority at start of string, invalid numeric priority, unknown date token removed silently, multiple date tokens where first wins without user feedback

---

---

## Algorithm 3: Task List Merging (Two-Way Sync)

**File:** `src/main/java/za/co/wethinkcode/taskmanager/util/TaskMergeService.java`  
**Methods:** `mergeTaskLists()`, `resolveTaskConflict()`

---

### Prompt 1 — Deconstruct the Algorithm

```
I'm trying to understand mergeTaskLists and resolveTaskConflict in
TaskMergeService.java.

My current understanding:
1. I think this function merges two versions of a task list — one local,
   one remote — and produces a single authoritative result
2. Inputs are two Map<String, Task> objects keyed by task ID, it returns
   a MergeResult containing five separate maps
3. I'm confused about why five maps are returned instead of just the
   merged result — what does the caller do with each one?

Help me understand this by:
1. Breaking mergeTaskLists and resolveTaskConflict into their distinct
   sections — for each section, describe what decision it makes and what
   it produces in business terms, not code terms
2. Walking through this concrete scenario step by step, showing which
   case applies for each task ID and what the final merged result looks
   like:
   local:  { "task-1": {status:IN_PROGRESS, updatedAt:T+5, tags:["work"]},
             "task-2": {status:TODO, updatedAt:T+3, tags:[]} }
   remote: { "task-1": {status:DONE, updatedAt:T+3, tags:["urgent"]},
             "task-3": {status:TODO, updatedAt:T+1, tags:[]} }
3. Naming the core technique used in resolveTaskConflict and explaining
   in one sentence why it has a domain exception for DONE status
4. Explaining why five maps are returned — what each represents and why
   the caller needs all five rather than just the merged result

After your explanation, ask me questions testing my understanding of the
conflict resolution strategy, the tag union behaviour, and what happens
when both tasks have identical updatedAt timestamps.
```

---

### Prompt 2 — Clarify Intent Behind the Code

```
I'm looking at resolveTaskConflict in TaskMergeService.java.

Context:
- resolveTaskConflict is called only when a task exists in both local
  and remote sources
- It starts by deep-copying the local task as the base
- It returns a ConflictResolution containing the merged task and two flags

Help me by:
1. Explaining why local is used as the base for the deep copy — why not
   remote, and what bug would occur if you skipped the copy and mutated
   the original directly?
2. Identifying one scenario where tag union semantics produce a result
   the user would consider wrong, and suggesting an alternative merge
   strategy with its tradeoff
3. Writing pseudocode for resolveTaskConflict that strips away all Java
   and reads as plain decision logic
4. Writing a Javadoc comment for resolveTaskConflict explaining the
   conflict strategy, the DONE exception, the tag behaviour, and what
   the three return values represent (maximum 10 lines)

Then give me 3–4 questions I should ask myself to validate my
understanding, and suggest safe experiments I could run to verify the
merge behaviour without touching production data.
```

---

### Prompt 3 — Untangle the Control Flow

```
I'm tracing the full logic of resolveTaskConflict including edge cases.

My current understanding of the control flow:
- Timestamp comparison runs first and sets base fields
- DONE exception runs independently after, overriding status if needed
- Tags are merged last using union semantics
- Both update flags can be set simultaneously

I'm unsure about:
1. What happens when both tasks have identical updatedAt timestamps —
   which side wins and is the result deterministic?
2. Whether the DONE exception can be reversed — what if a user marks a
   task DONE remotely but then reopens it locally?
3. Whether shouldUpdateLocal and shouldUpdateRemote can both be true for
   the same task, and under what conditions

Help me by:
1. Drawing the full control flow of resolveTaskConflict as a text
   decision tree — every branch, every flag change, including the
   equal-timestamp case
2. Identifying at least 3 silent failure modes or edge cases in
   mergeTaskLists or resolveTaskConflict
3. Predicting the exact output for each scenario with full working:
   Scenario A: local {status:TODO, updatedAt:T+1, tags:["work"]}
               remote {status:DONE, updatedAt:T+5, tags:["urgent"]}
   Scenario B: local {status:IN_PROGRESS, updatedAt:T+5, tags:["work"]}
               remote {status:IN_PROGRESS, updatedAt:T+5, tags:["urgent"]}
   Scenario C: local {status:DONE, updatedAt:T+3, tags:["work"]}
               remote {status:DONE, updatedAt:T+5, tags:["urgent"]}
4. For Scenario B with identical timestamps — which side wins, and does
   the algorithm guarantee a consistent result if the same sync runs
   twice? What could go wrong in a real distributed system?
```

---

### Fixes Implemented

**Fix 1 — Inject ConflictStrategy**

Conflict resolution strategy was hardcoded as last-write-wins with no way to override.

```java
public enum ConflictStrategy {
    TIMESTAMP,   // most recently updated version wins (default)
    LOCAL_WINS   // local always used as base regardless of timestamp
}

public TaskMergeService(ConflictStrategy strategy) {
    this.strategy = strategy;
}

public TaskMergeService() {
    this.strategy = ConflictStrategy.TIMESTAMP;
}
```

**Fix 2 — Simplified DONE resolution**

Original reopen detection using timestamp comparison was too broad — it fired whenever local was touched after completion, even for unrelated field edits, causing false reopens.

```java
// Simplified — DONE always wins over non-DONE regardless of timestamp
if (remoteTask.getStatus() == TaskStatus.DONE
        && localTask.getStatus() != TaskStatus.DONE) {
    mergedTask.setStatus(TaskStatus.DONE);
    mergedTask.setCompletedAt(remoteTask.getCompletedAt());
    shouldUpdateLocal = true;
}
```

**Deferred:** True reopen detection requires field-level diff tracking or an explicit `reopenedAt` timestamp. Test marked `@Disabled` with explanation preserved as a debt marker.

**Test result:** 10/10 passing, 1 skipped (deferred)

---

### Key Insights

- This is a **last-write-wins conflict resolution** algorithm with a domain exception for DONE status
- The deep copy of local as base is structurally required — if both reads and writes used the same object, the `shouldUpdateLocal` comparison at the end would always return false (same object reference)
- Five maps are returned because the caller is a sync engine, not a reader — it needs an action plan, not just the truth
- Tag union semantics mean tags are never deleted by a merge — this is a known limitation for tag removal workflows (CRDT-style per-tag tracking is the proper fix)
- Equal timestamps silently favour local — in production systems this requires vector clocks, server revision IDs, or explicit conflict records since wall-clock timestamps are unreliable across distributed nodes

---

---

## Reflection Questions (Apply After Each Algorithm)

Use these after completing all three prompts for any algorithm:

1. **In two sentences**, how would you explain this algorithm to a junior developer who has never seen it?
2. **Name one real change** you would make first and write the method signature it would require.
3. **If a new junior joined tomorrow**, how would you explain what this algorithm does as part of the wider system — one paragraph, plain English.

---

## Patterns to Watch For

| Pattern                              | Where it appeared                  |
| ------------------------------------ | ---------------------------------- |
| Weighted scoring model               | Algorithm 1 — calculateTaskScore   |
| Static constant extraction           | Algorithm 2 — Pattern.compile      |
| Deep copy before mutation            | Algorithm 3 — copyTask(local)      |
| Last-write-wins with domain override | Algorithm 3 — DONE exception       |
| Silent failure via regex non-match   | Algorithm 2 — !5, !urgent at start |
| Pre-computation before sort          | Algorithm 1 — scoreCache           |
| Union merge semantics                | Algorithm 3 — tag merging          |
| Debt marker via @Disabled            | Algorithm 3 — reopen detection     |
