package za.co.wethinkcode.taskmanager.util;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskPriority;
import za.co.wethinkcode.taskmanager.model.TaskStatus;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TaskPriorityManager {

    /** CORE scoring method (no user context) */
    public static int calculateTaskScore(Task task) {
        Map<TaskPriority, Integer> priorityWeights =
                Map.of(
                        TaskPriority.LOW, 1,
                        TaskPriority.MEDIUM, 2,
                        TaskPriority.HIGH, 3,
                        TaskPriority.URGENT, 4);

        int score = priorityWeights.getOrDefault(task.getPriority(), 0) * 10;

        // Due date scoring
        if (task.getDueDate() != null) {
            long daysUntilDue = ChronoUnit.DAYS.between(LocalDateTime.now(), task.getDueDate());
            if (daysUntilDue < 0) {
                score += 30;
            } else if (daysUntilDue == 0) {
                score += 20;
            } else if (daysUntilDue <= 2) {
                score += 15;
            } else if (daysUntilDue <= 7) {
                score += 10;
            }
        }

        // Status penalties
        if (task.getStatus() == TaskStatus.DONE) {
            score -= 50;
        } else if (task.getStatus() == TaskStatus.REVIEW) {
            score -= 15;
        }

        // Tag boost
        if (task.getTags() != null
                && task.getTags().stream()
                        .anyMatch(tag -> List.of("blocker", "critical", "urgent").contains(tag))) {
            score += 8;
        }

        // Recency boost
        long daysSinceUpdate = ChronoUnit.DAYS.between(task.getUpdatedAt(), LocalDateTime.now());
        if (daysSinceUpdate < 1) {
            score += 5;
        }

        return score;
    }

    /** Overloaded scoring method — adds +12 boost if task assigned to currentUserId */
    public static int calculateTaskScore(Task task, String currentUserId) {
        int score = calculateTaskScore(task);
        if (currentUserId != null && currentUserId.equals(task.getAssignedUserId())) {
            score += 12;
        }
        return score;
    }

    /** Sort tasks using full scoring with user context */
    public static List<Task> getTopPriorityTasks(
            List<Task> tasks, int limit, String currentUserId) {
        return tasks.stream()
                .sorted(
                        Comparator.comparingInt((Task t) -> calculateTaskScore(t, currentUserId))
                                .reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    /** Sort tasks by score descending (no user context) */
    public static List<Task> sortTasksByImportance(List<Task> tasks) {
        return tasks.stream()
                .sorted(Comparator.comparingInt((Task t) -> calculateTaskScore(t)).reversed())
                .collect(Collectors.toList());
    }

    /** Return top N tasks by score (no user context) */
    public static List<Task> getTopPriorityTasks(List<Task> tasks, int limit) {
        return sortTasksByImportance(tasks).stream().limit(limit).collect(Collectors.toList());
    }
}
