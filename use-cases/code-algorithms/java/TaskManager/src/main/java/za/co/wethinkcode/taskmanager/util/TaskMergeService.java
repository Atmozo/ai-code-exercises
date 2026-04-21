package za.co.wethinkcode.taskmanager.util;

import za.co.wethinkcode.taskmanager.model.Task;
import za.co.wethinkcode.taskmanager.model.TaskStatus;

import java.util.*;

public class TaskMergeService {

    /**
     * Strategy used to resolve conflicts when the same task exists in both local and remote
     * sources.
     *
     * <p>TIMESTAMP: most recently updated version wins (default) LOCAL_WINS: local version always
     * used as base regardless of timestamp
     */
    public enum ConflictStrategy {
        TIMESTAMP,
        LOCAL_WINS
    }

    private final ConflictStrategy strategy;

    public TaskMergeService(ConflictStrategy strategy) {
        this.strategy = strategy;
    }

    public TaskMergeService() {
        this.strategy = ConflictStrategy.TIMESTAMP;
    }

    /**
     * Merge two task lists with conflict resolution.
     *
     * @param localTasks Map of tasks from local source {task_id: task}
     * @param remoteTasks Map of tasks from remote source {task_id: task}
     * @return MergeResult containing all merged and categorized tasks
     */
    public MergeResult mergeTaskLists(Map<String, Task> localTasks, Map<String, Task> remoteTasks) {
        Map<String, Task> mergedTasks = new HashMap<>();
        Map<String, Task> toCreateRemote = new HashMap<>();
        Map<String, Task> toUpdateRemote = new HashMap<>();
        Map<String, Task> toCreateLocal = new HashMap<>();
        Map<String, Task> toUpdateLocal = new HashMap<>();

        Set<String> allTaskIds = new HashSet<>();
        allTaskIds.addAll(localTasks.keySet());
        allTaskIds.addAll(remoteTasks.keySet());

        for (String taskId : allTaskIds) {
            Task localTask = localTasks.get(taskId);
            Task remoteTask = remoteTasks.get(taskId);

            // Case 1: local only — push to remote
            if (localTask != null && remoteTask == null) {
                mergedTasks.put(taskId, localTask);
                toCreateRemote.put(taskId, localTask);

                // Case 2: remote only — pull to local
            } else if (localTask == null && remoteTask != null) {
                mergedTasks.put(taskId, remoteTask);
                toCreateLocal.put(taskId, remoteTask);

                // Case 3: both exist — resolve conflict
            } else {
                ConflictResolution resolution = resolveTaskConflict(localTask, remoteTask);
                Task mergedTask = resolution.getMergedTask();
                mergedTasks.put(taskId, mergedTask);

                if (resolution.isShouldUpdateLocal()) toUpdateLocal.put(taskId, mergedTask);
                if (resolution.isShouldUpdateRemote()) toUpdateRemote.put(taskId, mergedTask);
            }
        }

        return new MergeResult(
                mergedTasks, toCreateRemote, toUpdateRemote, toCreateLocal, toUpdateLocal);
    }

    /**
     * Resolves two versions of the same task into one merged result. Normally the newer updated
     * version is used as the base state. If either version is DONE, completed status is preserved —
     * unless local was updated after the remote completion, indicating an intentional reopen. Tags
     * are merged using union semantics; unique tags from both sides are kept. Inputs are not
     * mutated; a copied task is returned as the merged result.
     *
     * @param localTask local version of the task
     * @param remoteTask remote version of the task
     * @return merged task plus flags indicating whether local and/or remote copies should be
     *     updated to match the merged result
     */
    private ConflictResolution resolveTaskConflict(Task localTask, Task remoteTask) {
        Task mergedTask = copyTask(localTask);

        boolean shouldUpdateLocal = false;
        boolean shouldUpdateRemote = false;

        boolean localWins = strategy == ConflictStrategy.LOCAL_WINS;
        boolean remoteNewer = remoteTask.getUpdatedAt().isAfter(localTask.getUpdatedAt());

        /*
         * Base selection — non-status fields only.
         * Skipped entirely when either side is DONE; status block is authoritative then.
         */
        if (remoteTask.getStatus() != TaskStatus.DONE && localTask.getStatus() != TaskStatus.DONE) {

            if (!localWins && remoteNewer) {
                mergedTask.setTitle(remoteTask.getTitle());
                mergedTask.setDescription(remoteTask.getDescription());
                mergedTask.setPriority(remoteTask.getPriority());
                mergedTask.setDueDate(remoteTask.getDueDate());
                shouldUpdateLocal = true;
            } else {
                shouldUpdateRemote = true;
            }
        }

        /*
         * Status resolution — authoritative for all status decisions.
         * DONE always wins over non-DONE regardless of timestamp.
         * Reopen requires field-level diff tracking — deferred.
         */
        if (remoteTask.getStatus() == TaskStatus.DONE && localTask.getStatus() != TaskStatus.DONE) {
            // Remote DONE wins — apply to local
            mergedTask.setStatus(TaskStatus.DONE);
            mergedTask.setCompletedAt(remoteTask.getCompletedAt());
            shouldUpdateLocal = true;

        } else if (localTask.getStatus() == TaskStatus.DONE
                && remoteTask.getStatus() != TaskStatus.DONE) {
            // Local DONE wins — remote needs updating
            shouldUpdateRemote = true;

        } else if (remoteTask.getStatus() != localTask.getStatus()
                && remoteTask.getStatus() != TaskStatus.DONE
                && localTask.getStatus() != TaskStatus.DONE) {
            // Different active statuses — apply conflict strategy
            if (!localWins && remoteNewer) {
                mergedTask.setStatus(remoteTask.getStatus());
                shouldUpdateLocal = true;
            } else {
                shouldUpdateRemote = true;
            }
        }

        /*
         * Tag merge — union semantics.
         * Tags are never deleted by a merge; removal requires explicit action on both sides.
         */
        Set<String> allTags = new HashSet<>(localTask.getTags());
        allTags.addAll(remoteTask.getTags());
        mergedTask.setTags(new ArrayList<>(allTags));

        if (!new HashSet<>(mergedTask.getTags()).equals(new HashSet<>(localTask.getTags()))) {
            shouldUpdateLocal = true;
        }
        if (!new HashSet<>(mergedTask.getTags()).equals(new HashSet<>(remoteTask.getTags()))) {
            shouldUpdateRemote = true;
        }

        /*
         * Timestamp alignment — merged record takes the later of the two timestamps.
         */
        mergedTask.setUpdatedAt(
                localTask.getUpdatedAt().isAfter(remoteTask.getUpdatedAt())
                        ? localTask.getUpdatedAt()
                        : remoteTask.getUpdatedAt());

        return new ConflictResolution(mergedTask, shouldUpdateLocal, shouldUpdateRemote);
    }

    private Task copyTask(Task original) {
        Task copy = new Task(original.getTitle(), original.getDescription());
        copy.setId(original.getId());
        copy.setPriority(original.getPriority());
        copy.setStatus(original.getStatus());
        copy.setCreatedAt(original.getCreatedAt());
        copy.setUpdatedAt(original.getUpdatedAt());
        copy.setDueDate(original.getDueDate());
        copy.setCompletedAt(original.getCompletedAt());
        copy.setTags(new ArrayList<>(original.getTags()));
        return copy;
    }

    private static class ConflictResolution {
        private final Task mergedTask;
        private final boolean shouldUpdateLocal;
        private final boolean shouldUpdateRemote;

        public ConflictResolution(
                Task mergedTask, boolean shouldUpdateLocal, boolean shouldUpdateRemote) {
            this.mergedTask = mergedTask;
            this.shouldUpdateLocal = shouldUpdateLocal;
            this.shouldUpdateRemote = shouldUpdateRemote;
        }

        public Task getMergedTask() {
            return mergedTask;
        }

        public boolean isShouldUpdateLocal() {
            return shouldUpdateLocal;
        }

        public boolean isShouldUpdateRemote() {
            return shouldUpdateRemote;
        }
    }

    public static class MergeResult {
        private final Map<String, Task> mergedTasks;
        private final Map<String, Task> toCreateRemote;
        private final Map<String, Task> toUpdateRemote;
        private final Map<String, Task> toCreateLocal;
        private final Map<String, Task> toUpdateLocal;

        public MergeResult(
                Map<String, Task> mergedTasks,
                Map<String, Task> toCreateRemote,
                Map<String, Task> toUpdateRemote,
                Map<String, Task> toCreateLocal,
                Map<String, Task> toUpdateLocal) {
            this.mergedTasks = mergedTasks;
            this.toCreateRemote = toCreateRemote;
            this.toUpdateRemote = toUpdateRemote;
            this.toCreateLocal = toCreateLocal;
            this.toUpdateLocal = toUpdateLocal;
        }

        public Map<String, Task> getMergedTasks() {
            return mergedTasks;
        }

        public Map<String, Task> getToCreateRemote() {
            return toCreateRemote;
        }

        public Map<String, Task> getToUpdateRemote() {
            return toUpdateRemote;
        }

        public Map<String, Task> getToCreateLocal() {
            return toCreateLocal;
        }

        public Map<String, Task> getToUpdateLocal() {
            return toUpdateLocal;
        }
    }
}
