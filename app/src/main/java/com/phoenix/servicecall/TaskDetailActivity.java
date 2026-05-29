package com.phoenix.servicecall;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.utils.FirestoreRepository;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * TaskDetailActivity — full task view.
 *
 * All users: view all fields, mark done, request deletion, snooze (Phase 4).
 * Owner only: edit button in toolbar (customerName, serviceType, description).
 *
 * Receives taskId via Intent extra EXTRA_TASK_ID.
 * Loads task from the live office tasks list held in TaskViewModel.
 */
public class TaskDetailActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID = "task_id";

    // Views
    private Chip                    chipStatus, chipCallType;
    private TextView                tvCustomerName, tvPhone, tvServiceType,
            tvDescription, tvCreatedBy, tvCreatedAt,
            tvCompletedAt;
    private LinearLayout            layoutCompletedAt;
    private MaterialButton          btnMarkDone, btnSnooze, btnRequestDelete;
    private LinearProgressIndicator progress;

    // State
    private TaskViewModel viewModel;
    private SessionManager session;
    private Task           currentTask;
    private String         taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_task_detail);

        taskId  = getIntent().getStringExtra(EXTRA_TASK_ID);
        session = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        bindViews();
        setupToolbar();
        setupButtons();
        observeTask();
    }

    // ── View Binding ─────────────────────────────────────────────

    private void bindViews() {
        chipStatus        = findViewById(R.id.chip_status);
        chipCallType      = findViewById(R.id.chip_call_type);
        tvCustomerName    = findViewById(R.id.tv_customer_name);
        tvPhone           = findViewById(R.id.tv_phone);
        tvServiceType     = findViewById(R.id.tv_service_type);
        tvDescription     = findViewById(R.id.tv_description);
        tvCreatedBy       = findViewById(R.id.tv_created_by);
        tvCreatedAt       = findViewById(R.id.tv_created_at);
        tvCompletedAt     = findViewById(R.id.tv_completed_at);
        layoutCompletedAt = findViewById(R.id.layout_completed_at);
        btnMarkDone       = findViewById(R.id.btn_mark_done);
        btnSnooze         = findViewById(R.id.btn_snooze);
        btnRequestDelete  = findViewById(R.id.btn_request_delete);
        progress          = findViewById(R.id.progress);
    }

    // ── Toolbar ──────────────────────────────────────────────────

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Owner-only edit button
        View actionEdit = findViewById(R.id.action_edit);
        if (actionEdit != null) {
            actionEdit.setVisibility(session.isOwner() ? View.VISIBLE : View.GONE);
            actionEdit.setOnClickListener(v -> openEditDialog());
        }
    }

    // ── Button Setup ─────────────────────────────────────────────

    private void setupButtons() {
        btnMarkDone.setOnClickListener(v -> confirmMarkDone());

        btnSnooze.setOnClickListener(v ->
                // Phase 4 will open SnoozeActivity here
                Snackbar.make(btnSnooze, "Snooze will be available in the next update",
                        Snackbar.LENGTH_SHORT).show()
        );

        btnRequestDelete.setOnClickListener(v -> confirmDeleteRequest());
    }

    // ── Observe Task from ViewModel ──────────────────────────────

    private void observeTask() {
        // Task is already loaded in the shared ViewModel from OfficeTasksFragment.
        // Find it by taskId from the live list.
        viewModel.getOfficeTasks().observe(this, tasks -> {
            if (tasks == null) return;
            for (Task t : tasks) {
                if (taskId != null && taskId.equals(t.getTaskId())) {
                    currentTask = t;
                    populateUI(t);
                    return;
                }
            }
        });

        // Also observe myTasks in case it was opened from dashboard
        viewModel.getMyTasks().observe(this, tasks -> {
            if (tasks == null || currentTask != null) return;
            for (Task t : tasks) {
                if (taskId != null && taskId.equals(t.getTaskId())) {
                    currentTask = t;
                    populateUI(t);
                    return;
                }
            }
        });

        viewModel.getIsLoading().observe(this, loading ->
                progress.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getOperationResult().observe(this, result -> {
            if (result == null) return;
            if (result.startsWith("DONE:") || result.startsWith("DELETE_REQUESTED:")) {
                finish(); // go back to task list after action
            }
            if (result.equals("Task updated successfully")) {
                Snackbar.make(btnMarkDone, "Task updated", Snackbar.LENGTH_SHORT).show();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Snackbar.make(btnMarkDone, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.error))
                        .setTextColor(android.graphics.Color.WHITE)
                        .show();
            }
        });

        // Trigger load if ViewModel hasn't started yet (direct deep-link)
        if (viewModel.getOfficeTasks().getValue() == null) {
            viewModel.startListeningToOfficeTasks();
        }
    }

    // ── Populate UI ──────────────────────────────────────────────

    private void populateUI(Task task) {
        tvCustomerName.setText(task.getCustomerName());
        tvPhone.setText(task.getCustomerPhone());
        tvServiceType.setText(task.getServiceType());
        tvDescription.setText(
                task.getDescription() != null && !task.getDescription().isEmpty()
                        ? task.getDescription() : "No notes added");
        tvCreatedBy.setText(task.getCreatedByName());
        tvCreatedAt.setText(formatDateTime(
                task.getCreatedAt() != null ? task.getCreatedAt().toDate() : null));

        // Completed at
        if (task.isDone() && task.getCompletedAt() != null) {
            layoutCompletedAt.setVisibility(View.VISIBLE);
            tvCompletedAt.setText(formatDateTime(task.getCompletedAt().toDate()));
        } else {
            layoutCompletedAt.setVisibility(View.GONE);
        }

        // Status chip
        applyStatusChip(task);

        // Call type chip
        boolean isOffice = task.isOffice();
        chipCallType.setText(isOffice ? "Office" : "Personal");
        int callColor = getColor(isOffice ? R.color.primary : R.color.status_snoozed);
        int callBg    = getColor(isOffice ? R.color.primary_container : R.color.status_snoozed_bg);
        chipCallType.setTextColor(callColor);
        chipCallType.setChipBackgroundColor(android.content.res.ColorStateList.valueOf(callBg));

        // Button visibility based on status
        boolean isDone        = task.isDone();
        boolean isDelRequested = task.isDeletionRequested();

        btnMarkDone.setVisibility(isDone || isDelRequested ? View.GONE : View.VISIBLE);
        btnSnooze.setVisibility(isDone || isDelRequested ? View.GONE : View.VISIBLE);
        btnRequestDelete.setVisibility(isDelRequested || isDone ? View.GONE : View.VISIBLE);

        // If owner sees deletion_requested, show approve/reject instead
        if (isDelRequested && session.isOwner()) {
            showOwnerDeletionActions(task);
        }
    }

    private void applyStatusChip(Task task) {
        String label;
        int textColor, bgColor;

        if (task.isOverdue()) {
            label = "Overdue";
            textColor = getColor(R.color.status_overdue);
            bgColor   = getColor(R.color.status_overdue_bg);
        } else if (task.isSnoozed()) {
            label = "Snoozed";
            textColor = getColor(R.color.status_snoozed);
            bgColor   = getColor(R.color.status_snoozed_bg);
        } else if (task.isDone()) {
            label = "Done";
            textColor = getColor(R.color.status_done);
            bgColor   = getColor(R.color.status_done_bg);
        } else if (task.isDeletionRequested()) {
            label = "Deletion Requested";
            textColor = getColor(R.color.error);
            bgColor   = getColor(R.color.error_container);
        } else {
            label = "Pending";
            textColor = getColor(R.color.status_pending);
            bgColor   = getColor(R.color.status_pending_bg);
        }

        chipStatus.setText(label);
        chipStatus.setTextColor(textColor);
        chipStatus.setChipBackgroundColor(
                android.content.res.ColorStateList.valueOf(bgColor));
    }

    // ── Owner: Approve / Reject deletion ─────────────────────────

    private void showOwnerDeletionActions(Task task) {
        btnMarkDone.setVisibility(View.VISIBLE);
        btnMarkDone.setText("Approve Deletion");
        btnMarkDone.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getColor(R.color.error)));
        btnMarkDone.setOnClickListener(v -> confirmApproveDeletion(task));

        btnSnooze.setVisibility(View.VISIBLE);
        btnSnooze.setText("Reject Deletion");
        btnSnooze.setOnClickListener(v -> {
            viewModel.rejectDeletion(task.getTaskId(), task.getPreviousStatus());
            finish();
        });
    }

    // ── Confirm Dialogs ──────────────────────────────────────────

    private void confirmMarkDone() {
        if (currentTask == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Mark as Done")
                .setMessage("Mark this task as completed?")
                .setPositiveButton("Mark Done", (d, w) ->
                        viewModel.markDone(currentTask.getTaskId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmDeleteRequest() {
        if (currentTask == null) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("Request Deletion")
                .setMessage("This will send a deletion request to the owner for approval.")
                .setPositiveButton("Send Request", (d, w) ->
                        viewModel.requestDeletion(currentTask.getTaskId(), currentTask.getStatus()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmApproveDeletion(Task task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Approve Deletion")
                .setMessage("This will permanently remove the task from all views.")
                .setPositiveButton("Approve", (d, w) ->
                        viewModel.approveDeletion(task.getTaskId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Owner Edit Dialog ────────────────────────────────────────

    private void openEditDialog() {
        if (currentTask == null) return;

        // Inflate an edit form — reuse CreateTaskActivity for simplicity
        Intent intent = new Intent(this, EditTaskActivity.class);
        intent.putExtra(EditTaskActivity.EXTRA_TASK_ID,       currentTask.getTaskId());
        intent.putExtra(EditTaskActivity.EXTRA_CUSTOMER_NAME, currentTask.getCustomerName());
        intent.putExtra(EditTaskActivity.EXTRA_SERVICE_TYPE,  currentTask.getServiceType());
        intent.putExtra(EditTaskActivity.EXTRA_DESCRIPTION,   currentTask.getDescription());
        startActivity(intent);
    }

    // ── Helpers ──────────────────────────────────────────────────

    private String formatDateTime(Date date) {
        if (date == null) return "—";
        return new SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.getDefault()).format(date);
    }
}