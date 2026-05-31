package com.phoenix.servicecall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * PendingDeletionsActivity — owner only.
 *
 * Shows all tasks where status = "deletion_requested".
 * Owner can approve (soft-delete) or reject (revert to previous status).
 * Accessible from the toolbar admin icon in MainActivity.
 */
public class PendingDeletionsActivity extends AppCompatActivity {

    private RecyclerView              recycler;
    private LinearProgressIndicator   progressBar;
    private View                      layoutEmpty;
    private DeletionAdapter           adapter;
    private TaskViewModel             viewModel;
    private SessionManager            session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_pending_deletions);

        session = new SessionManager(this);

        // Guard: only owner
        if (!session.isOwner()) {
            finish();
            return;
        }

        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        setupToolbar();
        setupRecyclerView();
        observeViewModel();
        viewModel.startListeningToDeletionRequests();
    }

    // ── Toolbar ──────────────────────────────────────────────────

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── RecyclerView ─────────────────────────────────────────────

    private void setupRecyclerView() {
        recycler    = findViewById(R.id.recycler_deletions);
        progressBar = findViewById(R.id.progress_bar);
        layoutEmpty = findViewById(R.id.layout_empty);

        adapter = new DeletionAdapter(
                task -> confirmApprove(task),
                task -> confirmReject(task)
        );
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    // ── Observers ────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getDeletionRequests().observe(this, tasks -> {
            if (tasks == null || tasks.isEmpty()) {
                recycler.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                recycler.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                adapter.submitList(tasks);
            }
        });

        viewModel.getOperationResult().observe(this, result -> {
            if (result == null) return;
            if (result.startsWith("DELETE_APPROVED:")) {
                showSnackbar("Task deleted successfully", false);
            } else if (result.startsWith("DELETE_REJECTED:")) {
                showSnackbar("Deletion rejected — task restored", false);
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) showSnackbar(error, true);
        });
    }

    // ── Confirm Dialogs ──────────────────────────────────────────

    private void confirmApprove(Task task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Approve Deletion")
                .setMessage("This will permanently remove \"" + task.getCustomerName()
                        + "\" from all views. This cannot be undone.")
                .setPositiveButton("Approve", (d, w) ->
                        viewModel.approveDeletion(task.getTaskId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmReject(Task task) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Reject Deletion")
                .setMessage("The task will be restored to its previous status.")
                .setPositiveButton("Reject", (d, w) ->
                        viewModel.rejectDeletion(task.getTaskId(), task.getPreviousStatus()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Helpers ──────────────────────────────────────────────────

    private void showSnackbar(String msg, boolean isError) {
        View root = findViewById(android.R.id.content);
        Snackbar sb = Snackbar.make(root, msg, Snackbar.LENGTH_SHORT);
        if (isError) {
            sb.setBackgroundTint(getColor(R.color.error))
                    .setTextColor(android.graphics.Color.WHITE);
        }
        sb.show();
    }

    // ════════════════════════════════════════════════════════════
    // INNER ADAPTER
    // ════════════════════════════════════════════════════════════

    static class DeletionAdapter extends RecyclerView.Adapter<DeletionAdapter.VH> {

        interface OnApproveListener { void onApprove(Task task); }
        interface OnRejectListener  { void onReject(Task task);  }

        private List<Task>       items = new ArrayList<>();
        private final OnApproveListener approveListener;
        private final OnRejectListener  rejectListener;

        DeletionAdapter(OnApproveListener approveListener, OnRejectListener rejectListener) {
            this.approveListener = approveListener;
            this.rejectListener  = rejectListener;
        }

        void submitList(List<Task> list) {
            this.items = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_deletion_request, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position), approveListener, rejectListener);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView       tvCustomerName, tvAgentName, tvPhone, tvServiceType, tvTime;
            MaterialButton btnApprove, btnReject;

            VH(View v) {
                super(v);
                tvCustomerName = v.findViewById(R.id.tv_customer_name);
                tvAgentName    = v.findViewById(R.id.tv_agent_name);
                tvPhone        = v.findViewById(R.id.tv_phone);
                tvServiceType  = v.findViewById(R.id.tv_service_type);
                tvTime         = v.findViewById(R.id.tv_time);
                btnApprove     = v.findViewById(R.id.btn_approve);
                btnReject      = v.findViewById(R.id.btn_reject);
            }

            void bind(Task task,
                      OnApproveListener approveListener,
                      OnRejectListener  rejectListener) {
                tvCustomerName.setText(task.getCustomerName());
                tvAgentName.setText(task.getCreatedByName());
                tvPhone.setText(task.getCustomerPhone());
                tvServiceType.setText(task.getServiceType());
                tvTime.setText(task.getCreatedAt() != null
                        ? new SimpleDateFormat("dd MMM, h:mm a", Locale.getDefault())
                        .format(task.getCreatedAt().toDate())
                        : "");
                btnApprove.setOnClickListener(v -> approveListener.onApprove(task));
                btnReject.setOnClickListener(v  -> rejectListener.onReject(task));
            }
        }
    }
}