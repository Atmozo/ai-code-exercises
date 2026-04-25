# Test Plan: TaskPriorityManager

## Part 1 — Understanding What to Test

**File:** `src/main/java/za/co/wethinkcode/taskmanager/util/TaskPriorityManager.java`  
**Methods under test:** `calculateTaskScore`, `sortTasksByImportance`, `getTopPriorityTasks`  
**Language:** Java 17 / JUnit Jupiter 5 / AssertJ

---

## Behaviour Analysis — `calculateTaskScore`

### What it does (business terms)

Converts multiple task attributes into a single numeric score representing
how urgently a user should act on that task. Higher score = do this first.
Solves the problem: _"Out of all my tasks, which ones should I work on next?"_

### Inputs

- `Task task` — containing `TaskPriority`, `TaskStatus`, `dueDate`, `updatedAt`, `tags`

### Return value

- `int` — a ranking value with no fixed upper/lower bound
- Higher = more urgent, lower = less urgent, DONE tasks score negatively

### Core scoring factors

| Factor                         | Effect                        |
| ------------------------------ | ----------------------------- |
| Priority (LOW→URGENT)          | Base score ×10 (range: 10–40) |
| Due date proximity             | +10 to +30 bonus              |
| Overdue                        | +30 bonus                     |
| Status DONE                    | −50 penalty                   |
| Status REVIEW                  | −15 penalty                   |
| Tags (blocker/critical/urgent) | +8 bonus                      |
| Recently updated (<1 day)      | +5 bonus                      |
| Assigned to current user       | +12 bonus (new feature)       |

---

## Test Case List — `calculateTaskScore`

### Priority 1 — Core invariants (must pass first)

**TC-01: Priority ordering**

> HIGH priority task scores higher than MEDIUM priority task when all other fields are equal

**TC-02: Full priority ladder**

> URGENT > HIGH > MEDIUM > LOW when all other fields are equal

**TC-03: DONE task deprioritised**

> Task with status DONE scores lower than identical TODO task regardless of priority

**TC-04: Urgency can override priority**

> LOW priority task due today can score higher than HIGH priority task due in 10 days
> (valid composite behaviour, not a bug)

### Priority 2 — Null / missing value safety

**TC-05: Null dueDate — no crash, lower score**

> Task with null dueDate does not throw NullPointerException and scores lower
> than identical task with imminent due date

**TC-06: Null tags — treated as empty**

> Task with null tags produces same score as task with empty tag list

**TC-07: Null updatedAt — known defect**

> Task with null updatedAt throws NullPointerException in current implementation
> (ChronoUnit.DAYS.between has no null guard — defect to fix or document)

### Priority 3 — Specific scoring rules

**TC-08: Overdue boost**

> Overdue task scores 30 points higher than same task with no due date

**TC-09: Due today**

> Due-today task scores 20 points higher than same task with no due date

**TC-10: Due within 2 days**

> Due-within-2-days task scores 15 points higher than same task with no due date

**TC-11: Due within 7 days**

> Due-within-7-days task scores 10 points higher than same task with no due date

**TC-12: Due after 7 days**

> Due-after-7-days task scores 0 points higher than same task with no due date

**TC-13: Tag boost**

> Task tagged "blocker" scores 8 points higher than identical untagged task

**TC-14: Recency boost**

> Task updated within last 24 hours scores 5 points higher than stale equivalent

**TC-15: Assignment boost**

> Task assigned to current user scores 12 points higher than unassigned identical task

---

## Test Plan — All Three Methods

### Call chain

```
getTopPriorityTasks(tasks, n)
  └── sortTasksByImportance(tasks)
        └── calculateTaskScore(task)   ← called per task during sort
```

### Test structure rationale

Each layer must be tested independently so failures are diagnosable:

- `calculateTaskScore` fails → scoring bug
- `sortTasksByImportance` fails (scores pass) → ordering bug
- `getTopPriorityTasks` fails (sort passes) → selection/limit bug

### Test types by method

| Method                  | Test type   | Reason                              |
| ----------------------- | ----------- | ----------------------------------- |
| `calculateTaskScore`    | Unit        | Pure logic, no dependencies         |
| `sortTasksByImportance` | Unit        | Test ordering with controlled input |
| `sortTasksByImportance` | Integration | Verify ordering with real scoring   |
| `getTopPriorityTasks`   | Integration | Orchestrates multiple layers        |

**Note on mocking:** `TaskPriorityManager` uses static methods. True mocking
requires PowerMock or a design change. In practice, unit tests use carefully
constructed tasks with known scoring attributes to control effective scores
without mocking.

---

## Test Cases — `sortTasksByImportance`

**TC-16: Descending order**

> Given tasks with distinct scores, output is ordered highest-to-lowest

**TC-17: No tasks lost**

> Output list contains same number of tasks as input — no task dropped or duplicated

**TC-18: Single task**

> List with one task is returned unchanged

**TC-19: Empty list**

> Empty input returns empty output without error

**TC-20: All equal scores**

> Tasks with identical scores are all returned (order may vary — stability optional)

---

## Test Cases — `getTopPriorityTasks`

**TC-21: Returns correct top N**

> Given 5 tasks with distinct scores, `getTopPriorityTasks(tasks, 3)` returns
> the 3 highest-scoring tasks in descending order

**TC-22: Limit exceeds list size**

> `getTopPriorityTasks(tasks, 10)` on a 2-task list returns all 2 tasks

**TC-23: Limit of 1**

> Returns only the single highest-scoring task

**TC-24: Empty list**

> Empty input returns empty output without error

---

## Minimum Test Dataset — `getTopPriorityTasks`

Must include at least 5 tasks with varied attributes to force meaningful ranking:

| Task | Priority | Due date | Status | Assigned | Expected rank            |
| ---- | -------- | -------- | ------ | -------- | ------------------------ |
| A    | HIGH     | Overdue  | TODO   | user-1   | 1st (score 62)           |
| B    | MEDIUM   | Today    | TODO   | user-1   | 2nd/3rd (score 52, tied) |
| C    | HIGH     | +2 days  | TODO   | none     | 4th (score 50)           |
| D    | LOW      | Overdue  | TODO   | user-1   | 2nd/3rd (score 52, tied) |
| E    | MEDIUM   | None     | TODO   | none     | 5th (score 20)           |

This dataset ensures:

- Ranking is non-trivial (D beats C despite LOW priority — urgency + assignment wins)
- Composite scoring is exercised across all dimensions
- Tie-breaking is required (B and D tied at 52)

---

## Test Priority Checklist

### Must write first (core invariants)

- [x] TC-01: Priority ordering ✅
- [x] TC-03: DONE deprioritised ✅
- [x] TC-16: Sort descending order ✅
- [x] TC-21: Top N correct ✅

### Must write (safety)

- [x] TC-05: Null dueDate safety ✅
- [x] TC-19: Empty list (sortTasksByImportance) ✅
- [x] TC-24: Empty list (getTopPriorityTasks) ✅

### Should write (behaviour coverage)

- [x] TC-08: Overdue boost ✅
- [x] TC-09: Due today ✅
- [x] TC-10: Due within 2 days ✅
- [x] TC-11: Due within 7 days ✅
- [x] TC-12: Due after 7 days ✅
- [x] TC-14: Recency boost ✅
- [x] TC-15: Assignment boost ✅
- [x] TC-17: No tasks lost in sort ✅
- [x] TC-22: Limit exceeds list size ✅

### Nice to have (edge cases)

- [ ] TC-02: Full priority ladder
- [ ] TC-04: Urgency overrides priority
- [ ] TC-06: Null tags safety
- [ ] TC-07: Null updatedAt defect
- [ ] TC-13: Tag boost
- [ ] TC-18: Single task sort
- [ ] TC-20: All equal scores
- [ ] TC-23: Limit of 1

---

## Test Results — 17/17 Passing

| Test class                           | Tests  | Status        |
| ------------------------------------ | ------ | ------------- |
| `TaskPriorityManagerDueDateTest`     | 6      | ✅ All passed |
| `TaskPriorityManagerTDDTest`         | 6      | ✅ All passed |
| `TaskPriorityManagerIntegrationTest` | 5      | ✅ All passed |
| **Total**                            | **17** | **17/17**     |

**One test corrected during runs:**
`tasksUpdated23HoursAgoAnd47HoursAgoShouldHaveSameRecencyScore` was written
to expose a hypothetical `hours/24` truncation bug — but the actual
implementation uses `ChronoUnit.DAYS.between` correctly. 23h returns 0 days
(recency bonus) and 47h returns 1 day (no bonus) — correct and distinct.
Test was updated to verify two tasks both within 24 hours have equal scores.

---

## Prompts Used

### Exercise 1.1 — Behaviour Analysis

```
I'm learning how to test this function, and I want to understand
what behaviors I should test:

[calculateTaskScore — Java implementation from TaskPriorityManager.java]

Rather than generating tests for me, please:
1. Ask me questions about what I think this function does
2. After I answer, help identify any behaviors I missed
3. Ask me what edge cases I think should be tested
4. Help me identify additional edge cases I didn't think of
5. Ask me which test I should write first and why
```

**What this prompt produces:**

- Guided discovery of testable behaviours through questions
- Identification of null/missing value risks
- Prioritisation reasoning for which test to write first

**Why it works:**
Forces you to build your own mental model before seeing any code.
You identify more edge cases through reasoning than reading a pre-generated list.

---

### Exercise 1.2 — Test Planning

```
I'm learning to write tests for these related functions:

[calculateTaskScore, sortTasksByImportance, getTopPriorityTasks
— Java implementations from TaskPriorityManager.java]

Instead of writing tests for me, please:
1. Help me create a testing plan by asking me questions
2. For each behavior I identify, ask me how I would test it
3. If I miss something important, give me hints rather than answers
4. For each edge case we identify, ask me what I expect to happen
5. Help me create a checklist of tests I should write, organized by priority
```

**Key questions answered using this prompt:**

- Should you mock dependencies or use real implementations?
- How do you diagnose failures in a call chain?
- What minimum dataset forces meaningful ranking decisions?

---

### Exercise 2.1 — Improving a Single Test

```
I wrote this test for the following function:

Function: [calculateTaskScore — Java implementation]
My test: [paste your simple test]

Instead of rewriting it for me, please:
1. Ask me questions about what my test is trying to verify
2. Help me identify if my test is checking behavior or implementation details
3. Suggest how I could make the test's purpose clearer
4. Ask me what edge cases my test might be missing
5. Guide me in improving my assertions to be more precise
```

**Key improvements this prompt drives:**

- Replace `assertTrue(a > b)` with `assertEquals(expectedValue, actual)`
- Make all inputs explicit — no hidden defaults from constructors
- Use stale `updatedAt` to prevent recency bonus contamination
- Name tests to communicate what is held constant

---

### Exercise 2.2 — Due Date Testing

```
I'm trying to understand how to better test the due date calculation
portion of this function:

[calculateTaskScore — Java implementation]

I'm thinking of writing a test like this (pseudocode):
[rough outline of test idea]

Please:
1. Explain the principles of a good test for this specific functionality
2. Show me ONE example of a better test with comments explaining why it's better
3. Ask me questions about how I would improve my approach based on this example
4. Challenge me to identify what edge cases I should add
5. Guide me in writing more precise assertions
```

**Reusable pattern from this exercise:**

```java
// Differential approach — isolates one scoring factor
Task baseline = createBaseTask(); baseline.setDueDate(null);
Task variant  = createBaseTask(); variant.setDueDate(specificDate);

assertEquals(expectedBonus,
    calculateTaskScore(variant) - calculateTaskScore(baseline));
```

---

### Exercise 3.1 — TDD for New Feature

```
I want to practice Test-Driven Development to add a new feature:
Tasks assigned to the current user should get a score boost of +12.

Here's the current function:
[calculateTaskScore — Java implementation]

Instead of writing code and tests for me, please:
1. Ask me what I think the first test should be and why
2. Give me feedback on my proposed test
3. After I write the test, ask me what minimal code would make it pass
4. Once I've implemented the code, guide me on what test to add next
5. Help me understand when it's time to refactor vs. add new functionality
```

**TDD cycle followed:**

1. RED — write failing test that defines the feature
2. GREEN — add minimal code to pass (one conditional, no refactor)
3. Second RED — write inverse test (feature not applied when it shouldn't be)
4. GREEN — same implementation passes both tests

---

### Exercise 3.2 — TDD for Bug Fix

```
I want to practice TDD to fix a bug:
The calculation for "days since update" isn't working correctly.

Here's the current function:
[calculateTaskScore — Java implementation]

Please:
1. Ask me what test I would write to reproduce the bug
2. Help me understand if my test actually demonstrates the bug
3. Guide me in implementing a minimal fix
4. Ask me if there are any other tests I should add to prevent regression
```

**Key insight:** Tests define expected behaviour — they don't prove bugs.
A bug is discovered when the implementation contradicts the test.
If fixing a bug requires changing the test, the test encoded
implementation detail rather than behaviour — a design smell.

---

### Exercise 4.1 — Integration Testing

```
I want to create an integration test for the task priority workflow:

[calculateTaskScore, sortTasksByImportance, getTopPriorityTasks
— Java implementations]

Rather than writing the test for me, please:
1. Ask me what scenarios an integration test should verify
2. Guide me in designing test data that would exercise the entire workflow
3. Help me understand what assertions would verify the correct behavior
4. Ask me how I would structure the test to make it readable and maintainable
```

**What integration testing adds over unit tests:**

- Verifies all scoring rules interact correctly when combined
- Exposes hidden weighting bias (LOW + overdue + assigned can beat HIGH alone)
- Tests ranking stability under realistic distributions
- Catches tie-breaking edge cases

---

## Key Learnings

**1. Make all test inputs explicit**
Every field that affects scoring must be set explicitly in the test.
Hidden defaults (like `updatedAt = now` from the constructor) contaminate
scores and make tests fragile. Always use stale timestamps unless testing recency.

**2. Use differential assertions to isolate factors**
`assertEquals(30, overdueScore - baselineScore)` is safer than
`assertEquals(50, overdueScore)` — the differential isolates exactly
one scoring factor without depending on all other factors being zero.

**3. Write tests that define behaviour, not implementation**
Tests should survive a correct reimplementation. If fixing a bug
requires changing the test, the test was wrong — not the fix.

**4. Diagnose before fixing**
When a test fails, trace the actual values first.
The recency test failure revealed the bug was in the test expectation,
not the implementation — only discovered by tracing `ChronoUnit.DAYS.between`.

**5. TDD works best when you write the second test immediately**
The first test proves the feature exists. The second test proves
it is applied correctly (not always, not never). Both are required.
