package za.co.wethinkcode.taskmanager.util;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;
import za.co.wethinkcode.taskmanager.model.TaskStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TaskPriorityManager {

    /**
     * Calculate a priority score for a task based on multiple factors. Score combines priority
     * weight, due-date urgency, workflow status, critical/blocker tags, and recent activity. Higher
     * scores mean the task should appear sooner in priority lists. DONE tasks are heavily
     * penalised; REVIEW tasks are slightly penalised. Due-date boosts increase as deadlines
     * approach or pass. Returns a numeric score with no fixed upper or lower bound.
     */
    public static int calculateTaskScore(Task task) {
        // Base priority weights
        Map<TaskPriority, Integer> priorityWeights =
                Map.of(
                        TaskPriority.LOW, 1,
                        TaskPriority.MEDIUM, 2,
                        TaskPriority.HIGH, 3,
                        TaskPriority.URGENT, 4);

        // Calculate base score from priority
        int score = priorityWeights.getOrDefault(task.getPriority(), 0) * 10;

        // Add due date factor (higher score for tasks due sooner)
        if (task.getDueDate() != null) {
            long daysUntilDue = ChronoUnit.DAYS.between(LocalDateTime.now(), task.getDueDate());
            if (daysUntilDue < 0) { // Overdue tasks
                score += 30;
            } else if (daysUntilDue == 0) { // Due today
                score += 20;
            } else if (daysUntilDue <= 2) { // Due in next 2 days
                score += 15;
            } else if (daysUntilDue <= 7) { // Due in next week
                score += 10;
            }
        }

        // Reduce score for tasks that are completed or in review
        if (task.getStatus() == TaskStatus.DONE) {
            score -= 50;
        } else if (task.getStatus() == TaskStatus.REVIEW) {
            score -= 15;
        }

        // Boost score for tasks with certain tags
        if (task.getTags().stream()
                .anyMatch(tag -> List.of("blocker", "critical", "urgent").contains(tag))) {
            score += 8;
        }

        // Boost score for recently updated tasks
        long daysSinceUpdate = ChronoUnit.DAYS.between(task.getUpdatedAt(), LocalDateTime.now());
        if (daysSinceUpdate < 1) {
            score += 5;
        }

        return score;
    }

    /**
     * Sort tasks by calculated importance score (highest first). Scores are pre-computed once per
     * task to avoid redundant recalculation during sort comparisons.
     */
    public static List<Task> sortTasksByImportance(List<Task> tasks) {
        // Pre-compute scores once per task — avoids O(n log n) repeated calls
        // to calculateTaskScore during sort comparisons
        Map<Task, Integer> scoreCache = new HashMap<>();
        for (Task task : tasks) {
            scoreCache.put(task, calculateTaskScore(task));
        }

        return tasks.stream()
                .sorted(Comparator.comparing(scoreCache::get).reversed())
                .collect(Collectors.toList());
    }

    /** Return the top N priority tasks. */
    public static List<Task> getTopPriorityTasks(List<Task> tasks, int limit) {
        return sortTasksByImportance(tasks).stream().limit(limit).collect(Collectors.toList());
    }
}
