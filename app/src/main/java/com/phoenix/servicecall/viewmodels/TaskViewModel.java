package com.phoenix.servicecall.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.utils.FirestoreRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * TaskViewModel — manages task list state for:
 *   - OfficeTasksFragment  (shared office task pool)
 *   - DashboardFragment    (today's summary + per-agent stats)
 *   - PendingDeletionsActivity (owner only)
 *
 * Sorting order (OfficeTasksFragment):
 *   1. Overdue   (pending + reminderSentAt != null)
 *   2. Pending
 *   3. Snoozed
 *   4. Done      (shown in collapsed section)
 *
 * All Firestore listeners are cleaned up in onCleared().
 */
public class TaskViewModel extends ViewModel {

    // ── LiveData ─────────────────────────────────────────────────
    private final MutableLiveData<List<Task>>  officeTasks       = new MutableLiveData<>();
    private final MutableLiveData<List<Task>>  myTasks           = new MutableLiveData<>();
    private final MutableLiveData<List<Task>>  todaysTasks       = new MutableLiveData<>();
    private final MutableLiveData<List<Task>>  deletionRequests  = new MutableLiveData<>();
    private final MutableLiveData<String>      errorMessage      = new MutableLiveData<>();
    private final MutableLiveData<Boolean>     isLoading         = new MutableLiveData<>(false);
    private final MutableLiveData<String>      operationResult   = new MutableLiveData<>();

    // ── Listener registrations (cleaned up in onCleared) ─────────
    private ListenerRegistration officeTasksListener;
    private ListenerRegistration myTasksListener;
    private ListenerRegistration todaysTasksListener;
    private ListenerRegistration deletionListener;

    private final FirestoreRepository repo = FirestoreRepository.getInstance();

    // ════════════════════════════════════════════════════════════
    // OFFICE TASKS — real-time listener
    // ════════════════════════════════════════════════════════════

    /**
     * Start listening to all office tasks.
     * Call from OfficeTasksFragment.onViewCreated().
     * Results are sorted by status priority before posting.
     */
    public void startListeningToOfficeTasks() {
        if (officeTasksListener != null) return; // already listening

        officeTasksListener = repo.listenToOfficeTasks(
                tasks -> officeTasks.postValue(sortByStatusPriority(tasks)),
                e    -> errorMessage.postValue("Failed to load tasks: " + e.getMessage())
        );
    }

    public LiveData<List<Task>> getOfficeTasks() { return officeTasks; }

    // ════════════════════════════════════════════════════════════
    // MY TASKS — real-time listener (dashboard)
    // ════════════════════════════════════════════════════════════

    public void startListeningToMyTasks(String uid) {
        if (myTasksListener != null) return;

        myTasksListener = repo.listenToMyTasks(uid,
                tasks -> myTasks.postValue(tasks),
                e    -> errorMessage.postValue("Failed to load your tasks: " + e.getMessage())
        );
    }

    public LiveData<List<Task>> getMyTasks() { return myTasks; }

    // ════════════════════════════════════════════════════════════
    // TODAY'S TASKS — real-time listener (dashboard cards)
    // ════════════════════════════════════════════════════════════

    public void startListeningToTodaysTasks(String uid, boolean isOwner) {
        if (todaysTasksListener != null) return;

        todaysTasksListener = repo.listenToTodaysTasks(uid, isOwner,
                tasks -> todaysTasks.postValue(tasks),
                e    -> errorMessage.postValue("Failed to load today's tasks: " + e.getMessage())
        );
    }

    public LiveData<List<Task>> getTodaysTasks() { return todaysTasks; }

    // ════════════════════════════════════════════════════════════
    // DELETION REQUESTS — real-time listener (owner only)
    // ════════════════════════════════════════════════════════════

    public void startListeningToDeletionRequests() {
        if (deletionListener != null) return;

        deletionListener = repo.listenToDeletionRequests(
                tasks -> deletionRequests.postValue(tasks),
                e    -> errorMessage.postValue("Failed to load deletion requests: " + e.getMessage())
        );
    }

    public LiveData<List<Task>> getDeletionRequests() { return deletionRequests; }

    // ════════════════════════════════════════════════════════════
    // TASK CREATION
    // ════════════════════════════════════════════════════════════

    public void createTask(Task task) {
        isLoading.setValue(true);
        repo.createTask(task,
                taskId -> {
                    isLoading.postValue(false);
                    operationResult.postValue("Task created successfully");
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue("Failed to create task: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════════════
    // MARK DONE / UNDO
    // ════════════════════════════════════════════════════════════

    public void markDone(String taskId) {
        repo.markTaskDone(taskId,
                v -> operationResult.postValue("DONE:" + taskId),
                e -> errorMessage.postValue("Could not mark task done: " + e.getMessage()));
    }

    public void undoMarkDone(String taskId) {
        repo.undoMarkDone(taskId,
                v -> operationResult.postValue("UNDO_DONE:" + taskId),
                e -> errorMessage.postValue("Could not undo: " + e.getMessage()));
    }

    // ════════════════════════════════════════════════════════════
    // DELETION FLOW
    // ════════════════════════════════════════════════════════════

    public void requestDeletion(String taskId, String currentStatus) {
        repo.requestTaskDeletion(taskId, currentStatus,
                v -> operationResult.postValue("DELETE_REQUESTED:" + taskId),
                e -> errorMessage.postValue("Could not request deletion: " + e.getMessage()));
    }

    public void approveDeletion(String taskId) {
        repo.approveTaskDeletion(taskId,
                v -> operationResult.postValue("DELETE_APPROVED:" + taskId),
                e -> errorMessage.postValue("Could not approve deletion: " + e.getMessage()));
    }

    public void rejectDeletion(String taskId, String previousStatus) {
        repo.rejectTaskDeletion(taskId, previousStatus,
                v -> operationResult.postValue("DELETE_REJECTED:" + taskId),
                e -> errorMessage.postValue("Could not reject deletion: " + e.getMessage()));
    }

    // ════════════════════════════════════════════════════════════
    // EDIT TASK (owner only)
    // ════════════════════════════════════════════════════════════

    public void updateTask(String taskId, String customerName,
                           String serviceType, String description) {
        isLoading.setValue(true);
        repo.updateTask(taskId, customerName, serviceType, description,
                v -> {
                    isLoading.postValue(false);
                    operationResult.postValue("Task updated successfully");
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue("Could not update task: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════════════
    // FILTERED QUERY (dashboard filter bar)
    // ════════════════════════════════════════════════════════════

    public void applyFilter(String uid, String status, String serviceType,
                            Timestamp from, Timestamp to) {
        isLoading.setValue(true);
        repo.getFilteredTasks(uid, status, serviceType, from, to,
                tasks -> {
                    isLoading.postValue(false);
                    officeTasks.postValue(sortByStatusPriority(tasks));
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue("Filter failed: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════════════
    // DASHBOARD STATS HELPERS
    // ════════════════════════════════════════════════════════════

    /** Count tasks by status from a list — used for summary cards */
    public static int countByStatus(List<Task> tasks, String status) {
        if (tasks == null) return 0;
        int count = 0;
        for (Task t : tasks) {
            if (status.equals(t.getStatus())) count++;
        }
        return count;
    }

    /** Count overdue tasks (pending + reminder already sent) */
    public static int countOverdue(List<Task> tasks) {
        if (tasks == null) return 0;
        int count = 0;
        for (Task t : tasks) {
            if (t.isOverdue()) count++;
        }
        return count;
    }

    /** Filter tasks for a specific agent UID */
    public static List<Task> filterByAgent(List<Task> tasks, String uid) {
        List<Task> result = new ArrayList<>();
        if (tasks == null) return result;
        for (Task t : tasks) {
            if (uid.equals(t.getCreatedByUid())) result.add(t);
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // SORTING
    // ════════════════════════════════════════════════════════════

    /**
     * Sorts tasks by status priority:
     *   1 — Overdue (pending + reminderSentAt != null)
     *   2 — Pending
     *   3 — Snoozed
     *   4 — Done
     *   5 — Deletion requested (shown at end of active section)
     *
     * Within each group, newest first (createdAt descending).
     */
    public static List<Task> sortByStatusPriority(List<Task> tasks) {
        if (tasks == null) return new ArrayList<>();

        List<Task> sorted = new ArrayList<>(tasks);
        sorted.sort(Comparator
                .comparingInt(TaskViewModel::statusPriority)
                .thenComparing(t -> {
                    if (t.getCreatedAt() == null) return 0L;
                    return -t.getCreatedAt().getSeconds(); // newest first within group
                }));
        return sorted;
    }

    private static int statusPriority(Task task) {
        if (task.isOverdue())              return 1;
        if (task.isPending())              return 2;
        if (task.isSnoozed())              return 3;
        if (task.isDeletionRequested())    return 4;
        if (task.isDone())                 return 5;
        return 6;
    }

    // ════════════════════════════════════════════════════════════
    // SHARED LIVEDATA
    // ════════════════════════════════════════════════════════════

    public LiveData<String>  getErrorMessage()    { return errorMessage; }
    public LiveData<Boolean> getIsLoading()        { return isLoading; }
    public LiveData<String>  getOperationResult() { return operationResult; }

    // ════════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        if (officeTasksListener != null) officeTasksListener.remove();
        if (myTasksListener     != null) myTasksListener.remove();
        if (todaysTasksListener != null) todaysTasksListener.remove();
        if (deletionListener    != null) deletionListener.remove();
    }
}