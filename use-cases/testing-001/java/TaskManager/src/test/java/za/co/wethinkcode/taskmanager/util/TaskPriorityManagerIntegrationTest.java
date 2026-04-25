package za.co.wethinkcode.taskmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;
import za.co.wethinkcode.taskmanager.model.TaskStatus;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

class TaskPriorityManagerIntegrationTest {

    /**
     * Integration test: verifies calculateTaskScore + sortTasksByImportance + getTopPriorityTasks
     * work correctly together across all scoring rules.
     *
     * <p>Manual score calculations (currentUserId = "user-1", stale updatedAt):
     *
     * <p>Task A: HIGH(30) + overdue(30) + assigned(12) + no-recency(0) = 62 Task B: MEDIUM(20) +
     * today(20) + assigned(12) + no-recency(0) = 52 Task C: HIGH(30) + 2days(15) + unassigned(0) +
     * recency(5) = 50 Task D: LOW(10) + overdue(30) + assigned(12) + no-recency(0) = 52 Task E:
     * MEDIUM(20) + none(0) + unassigned(0) + no-recency(0) = 20
     *
     * <p>Expected top 3: A(62), then B and D tied at 52, then C(50)
     */
    @Test
    void getTopPriorityTasksReturnsCorrectRankingAcrossAllScoringRules() {
        String currentUserId = "user-1";
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime stale = now.minusDays(2);

        // Task A: HIGH, overdue, assigned — should rank first
        Task taskA =
                new Task("A", "", TaskPriority.HIGH, now.minusDays(1), Collections.emptyList());
        taskA.setAssignedUserId("user-1");
        taskA.setUpdatedAt(stale);

        // Task B: MEDIUM, due today, assigned — tied at 52
        Task taskB =
                new Task(
                        "B",
                        "",
                        TaskPriority.MEDIUM,
                        now.toLocalDate().atTime(23, 59),
                        Collections.emptyList());
        taskB.setAssignedUserId("user-1");
        taskB.setUpdatedAt(stale);

        // Task C: HIGH, due in 2 days, unassigned, recently updated — scores 50
        Task taskC = new Task("C", "", TaskPriority.HIGH, now.plusDays(2), Collections.emptyList());
        taskC.setAssignedUserId(null);
        taskC.setUpdatedAt(now.minusHours(10)); // recent → +5

        // Task D: LOW, overdue, assigned — tied with B at 52
        Task taskD = new Task("D", "", TaskPriority.LOW, now.minusDays(2), Collections.emptyList());
        taskD.setAssignedUserId("user-1");
        taskD.setUpdatedAt(stale);

        // Task E: MEDIUM, no due date, unassigned — should rank last
        Task taskE = new Task("E", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        taskE.setAssignedUserId(null);
        taskE.setUpdatedAt(stale);

        List<Task> tasks = Arrays.asList(taskA, taskB, taskC, taskD, taskE);

        // Act
        List<Task> top3 = TaskPriorityManager.getTopPriorityTasks(tasks, 3, currentUserId);

        // Assert
        assertEquals(3, top3.size(), "Should return exactly 3 tasks");

        // A must be first — highest score at 62, no ties
        assertEquals("A", top3.get(0).getTitle(), "Task A (score 62) should be ranked first");

        // B and D are tied at 52 — both must be in top 3
        List<String> top3Titles = top3.stream().map(Task::getTitle).collect(Collectors.toList());

        assertTrue(top3Titles.contains("B"), "Task B (score 52) should be in top 3");
        assertTrue(top3Titles.contains("D"), "Task D (score 52) should be in top 3");

        // C and E must not be in top 3
        assertFalse(top3Titles.contains("C"), "Task C (score 50) should not be in top 3");
        assertFalse(top3Titles.contains("E"), "Task E (score 20) should not be in top 3");
    }

    @Test
    void doneTaskSinksToBottomRegardlessOfPriority() {
        LocalDateTime stale = LocalDateTime.now().minusDays(2);

        Task urgent = new Task("Urgent", "", TaskPriority.URGENT, null, Collections.emptyList());
        urgent.setUpdatedAt(stale);
        urgent.setStatus(TaskStatus.DONE);

        Task low = new Task("Low", "", TaskPriority.LOW, null, Collections.emptyList());
        low.setUpdatedAt(stale);

        List<Task> tasks = Arrays.asList(urgent, low);
        List<Task> sorted = TaskPriorityManager.sortTasksByImportance(tasks);

        assertEquals(
                "Low",
                sorted.get(0).getTitle(),
                "LOW priority TODO task should rank above URGENT DONE task");
        assertEquals(
                "Urgent",
                sorted.get(1).getTitle(),
                "DONE task should sink to bottom regardless of priority");
    }

    @Test
    void sortPreservesAllTasksWithoutLossOrDuplication() {
        LocalDateTime stale = LocalDateTime.now().minusDays(2);

        List<Task> tasks =
                Arrays.asList(
                        new Task("T1", "", TaskPriority.HIGH, null, Collections.emptyList()),
                        new Task("T2", "", TaskPriority.LOW, null, Collections.emptyList()),
                        new Task("T3", "", TaskPriority.MEDIUM, null, Collections.emptyList()));
        tasks.forEach(t -> t.setUpdatedAt(stale));

        List<Task> sorted = TaskPriorityManager.sortTasksByImportance(tasks);

        assertEquals(3, sorted.size(), "Sort should preserve all 3 tasks");
        assertTrue(sorted.stream().anyMatch(t -> t.getTitle().equals("T1")));
        assertTrue(sorted.stream().anyMatch(t -> t.getTitle().equals("T2")));
        assertTrue(sorted.stream().anyMatch(t -> t.getTitle().equals("T3")));
    }

    @Test
    void emptyListReturnsEmptyWithoutError() {
        List<Task> result = TaskPriorityManager.getTopPriorityTasks(Collections.emptyList(), 3);
        assertTrue(result.isEmpty(), "Empty input should return empty list");
    }

    @Test
    void limitExceedingListSizeReturnsAllTasks() {
        LocalDateTime stale = LocalDateTime.now().minusDays(2);

        List<Task> tasks =
                Arrays.asList(
                        new Task("T1", "", TaskPriority.HIGH, null, Collections.emptyList()),
                        new Task("T2", "", TaskPriority.LOW, null, Collections.emptyList()));
        tasks.forEach(t -> t.setUpdatedAt(stale));

        List<Task> result = TaskPriorityManager.getTopPriorityTasks(tasks, 10);

        assertEquals(
                2, result.size(), "Requesting more tasks than available should return all tasks");
    }
}
