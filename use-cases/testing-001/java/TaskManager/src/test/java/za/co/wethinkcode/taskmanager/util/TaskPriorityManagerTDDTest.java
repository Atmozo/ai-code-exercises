package za.co.wethinkcode.taskmanager.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;

import java.time.LocalDateTime;
import java.util.Collections;

class TaskPriorityManagerTDDTest {

    private Task createNeutralTask(String title) {
        Task task = new Task(title, "", TaskPriority.MEDIUM, null, Collections.emptyList());
        task.setUpdatedAt(LocalDateTime.now().minusDays(2)); // stale — no recency bonus
        return task;
    }

    // ─────────────────────────────────────────────────────────────
    // Exercise 3.1 — Assignment boost feature (+12)
    // ─────────────────────────────────────────────────────────────

    @Test
    void assignedTaskGets12PointBoostComparedToUnassignedTask() {
        // RED: this test drives the new calculateTaskScore(task, currentUserId) overload
        String currentUserId = "user-1";

        Task base = createNeutralTask("base");
        base.setAssignedUserId(null);

        Task assigned = createNeutralTask("assigned");
        assigned.setAssignedUserId("user-1");

        int baseScore = TaskPriorityManager.calculateTaskScore(base, currentUserId);
        int assignedScore = TaskPriorityManager.calculateTaskScore(assigned, currentUserId);

        assertEquals(
                12,
                assignedScore - baseScore,
                "Task assigned to current user should score exactly 12 points more than"
                        + " unassigned");
    }

    @Test
    void unassignedTaskDoesNotReceiveAssignmentBoost() {
        String currentUserId = "user-1";

        Task assigned = createNeutralTask("assigned");
        assigned.setAssignedUserId("user-1");

        Task unassigned = createNeutralTask("unassigned");
        unassigned.setAssignedUserId(null);

        // Differential — boost on assigned vs unassigned should be exactly 12, no more
        int diff =
                TaskPriorityManager.calculateTaskScore(assigned, currentUserId)
                        - TaskPriorityManager.calculateTaskScore(unassigned, currentUserId);

        assertEquals(
                12,
                diff,
                "Only assigned task should receive boost — difference should be exactly 12");
    }

    @Test
    void taskAssignedToDifferentUserDoesNotReceiveBoost() {
        String currentUserId = "user-1";

        Task base = createNeutralTask("base");
        base.setAssignedUserId(null);

        Task otherUser = createNeutralTask("other");
        otherUser.setAssignedUserId("user-99"); // different user

        int diff =
                TaskPriorityManager.calculateTaskScore(otherUser, currentUserId)
                        - TaskPriorityManager.calculateTaskScore(base, currentUserId);

        assertEquals(
                0,
                diff,
                "Task assigned to a different user should not receive the current user boost");
    }

    @Test
    void nullCurrentUserIdDoesNotCrashAndAppliesNoBoost() {
        Task assigned = createNeutralTask("assigned");
        assigned.setAssignedUserId("user-1");

        // Should not throw, should behave as no-user context
        assertDoesNotThrow(() -> TaskPriorityManager.calculateTaskScore(assigned, null));

        int score = TaskPriorityManager.calculateTaskScore(assigned, null);
        int baseline = TaskPriorityManager.calculateTaskScore(createNeutralTask("base"), null);

        assertEquals(
                0,
                score - baseline,
                "Null currentUserId should apply no boost regardless of task assignment");
    }

    // ─────────────────────────────────────────────────────────────
    // Exercise 3.2 — Recency bug fix
    // ─────────────────────────────────────────────────────────────

    @Test
    void tasksUpdated23HoursAgoAnd47MinutesAgoShouldBothBeConsideredRecent() {
        LocalDateTime now = LocalDateTime.now();

        Task recent = new Task("recent", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        recent.setUpdatedAt(now.minusMinutes(47)); // < 1 day → recent

        Task alsoRecent =
                new Task("alsoRecent", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        alsoRecent.setUpdatedAt(now.minusHours(23)); // < 1 day → also recent

        assertEquals(
                TaskPriorityManager.calculateTaskScore(recent),
                TaskPriorityManager.calculateTaskScore(alsoRecent),
                "Both tasks updated within 24 hours should have the same recency score");
    }

    @Test
    void taskUpdatedMoreThan24HoursAgoShouldNotReceiveRecencyBonus() {
        LocalDateTime now = LocalDateTime.now();

        Task stale = new Task("stale", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        stale.setUpdatedAt(now.minusHours(25)); // more than 1 full day

        Task fresh = new Task("fresh", "", TaskPriority.MEDIUM, null, Collections.emptyList());
        fresh.setUpdatedAt(now.minusMinutes(30)); // recently updated

        int diff =
                TaskPriorityManager.calculateTaskScore(fresh)
                        - TaskPriorityManager.calculateTaskScore(stale);

        assertEquals(
                5,
                diff,
                "Recently updated task should score exactly 5 points more than stale task");
    }
}
