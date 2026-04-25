package za.co.wethinkcode.taskmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;

import java.time.LocalDateTime;
import java.util.Collections;

class TaskPriorityManagerDueDateTest {

    /**
     * Creates a baseline task with all scoring factors neutralised: - MEDIUM priority (score = 20)
     * - no due date (no urgency bonus) - stale updatedAt (no recency bonus) - no tags (no tag
     * bonus) - TODO status (no penalty)
     */
    private Task createBaseTask() {
        Task task = new Task("Test", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        task.setUpdatedAt(LocalDateTime.now().minusDays(2)); // stale — prevent recency +5
        return task;
    }

    @Test
    void overdueTaskAdds30PointsToScore() {
        Task baseline = createBaseTask();

        Task overdue = createBaseTask();
        overdue.setDueDate(LocalDateTime.now().minusDays(2));

        int diff =
                TaskPriorityManager.calculateTaskScore(overdue)
                        - TaskPriorityManager.calculateTaskScore(baseline);

        assertEquals(
                30,
                diff,
                "Overdue task should score exactly 30 points more than identical task with no due"
                    + " date");
    }

    @Test
    void dueTodayAdds20PointsToScore() {
        Task baseline = createBaseTask();

        // Use end of today to avoid millisecond race condition with DAYS.between
        Task today = createBaseTask();
        today.setDueDate(LocalDateTime.now().toLocalDate().atTime(23, 59));

        int diff =
                TaskPriorityManager.calculateTaskScore(today)
                        - TaskPriorityManager.calculateTaskScore(baseline);

        assertEquals(
                20,
                diff,
                "Task due today should score exactly 20 points more than identical task with no due"
                    + " date");
    }

    @Test
    void dueWithin2DaysAdds15PointsToScore() {
        Task baseline = createBaseTask();

        Task soon = createBaseTask();
        soon.setDueDate(LocalDateTime.now().plusDays(2));

        int diff =
                TaskPriorityManager.calculateTaskScore(soon)
                        - TaskPriorityManager.calculateTaskScore(baseline);

        assertEquals(
                15,
                diff,
                "Task due within 2 days should score exactly 15 points more than identical task"
                    + " with no due date");
    }

    @Test
    void dueWithin7DaysAdds10PointsToScore() {
        Task baseline = createBaseTask();

        Task week = createBaseTask();
        week.setDueDate(LocalDateTime.now().plusDays(5));

        int diff =
                TaskPriorityManager.calculateTaskScore(week)
                        - TaskPriorityManager.calculateTaskScore(baseline);

        assertEquals(
                10,
                diff,
                "Task due within 7 days should score exactly 10 points more than identical task"
                    + " with no due date");
    }

    @Test
    void dueAfter7DaysAddsNoPointsToScore() {
        Task baseline = createBaseTask();

        Task future = createBaseTask();
        future.setDueDate(LocalDateTime.now().plusDays(10));

        int diff =
                TaskPriorityManager.calculateTaskScore(future)
                        - TaskPriorityManager.calculateTaskScore(baseline);

        assertEquals(
                0,
                diff,
                "Task due after 7 days should score the same as identical task with no due date");
    }

    @Test
    void highPriorityScoresHigherThanMedium_whenAllOtherFactorsNeutral() {
        LocalDateTime stale = LocalDateTime.now().minusDays(2);

        Task high = new Task("High", "", TaskPriority.HIGH, null, Collections.emptyList());
        high.setUpdatedAt(stale);

        Task medium = new Task("Medium", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        medium.setUpdatedAt(stale);

        int highScore = TaskPriorityManager.calculateTaskScore(high);
        int mediumScore = TaskPriorityManager.calculateTaskScore(medium);

        assertEquals(30, highScore, "HIGH priority with no other factors should score 30");
        assertEquals(20, mediumScore, "MEDIUM priority with no other factors should score 20");
        assertTrue(highScore > mediumScore);
    }
}
