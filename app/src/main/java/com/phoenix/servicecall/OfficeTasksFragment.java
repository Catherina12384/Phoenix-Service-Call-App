package com.phoenix.servicecall;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.phoenix.servicecall.adapters.TaskAdapter;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

/**
 * OfficeTasksFragment — Tab 2.
 * Displays the shared office task pool in real-time.
 *
 * Swipe right → mark done → undo Snackbar (5 sec)
 * Swipe left  → "Snooze coming in next update" toast
 * Tap task    → TaskDetailActivity
 */
public class OfficeTasksFragment extends Fragment {

    private TaskViewModel             viewModel;
    private TaskAdapter               adapter;
    private RecyclerView              recycler;
    private LinearProgressIndicator   progressBar;
    private View                      layoutEmpty;

    public static OfficeTasksFragment newInstance() {
        return new OfficeTasksFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_office_tasks, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        progressBar  = view.findViewById(R.id.progress_bar);
        layoutEmpty  = view.findViewById(R.id.layout_empty);
        recycler     = view.findViewById(R.id.recycler_tasks);

        // ViewModel is scoped to Activity so Dashboard and TaskList share the same data
        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        setupRecyclerView(view);
        observeViewModel();
        viewModel.startListeningToOfficeTasks();
    }

    // ── RecyclerView Setup ───────────────────────────────────────

    private void setupRecyclerView(View root) {
        adapter = new TaskAdapter(
                requireContext(),
                task -> openTaskDetail(task),
                (task, pos) -> onSwipeRight(task, pos, root),
                (task, pos) -> onSwipeLeft(task, root)
        );

        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        recycler.setAdapter(adapter);
        adapter.getSwipeHelper().attachToRecyclerView(recycler);
    }

    // ── Swipe Handlers ───────────────────────────────────────────

    private void onSwipeRight(Task task, int position, View root) {
        // Mark done immediately
        viewModel.markDone(task.getTaskId());

        // Undo Snackbar — 5 seconds
        Snackbar.make(root, task.getCustomerName() + " marked as done", 5000)
                .setAction("UNDO", v -> viewModel.undoMarkDone(task.getTaskId()))
                .setActionTextColor(requireContext().getColor(R.color.primary_container))
                .setBackgroundTint(requireContext().getColor(R.color.status_done))
                .setTextColor(android.graphics.Color.WHITE)
                .show();
    }

    private void onSwipeLeft(Task task, View root) {
        // Phase 4 will implement the full snooze flow
        Snackbar.make(root, "Snooze will be available in the next update", Snackbar.LENGTH_SHORT)
                .show();
        // Refresh list to restore the swiped item
        adapter.notifyDataSetChanged();
    }

    // ── Task Detail Navigation ───────────────────────────────────

    private void openTaskDetail(Task task) {
        Intent intent = new Intent(requireContext(), TaskDetailActivity.class);
        intent.putExtra(TaskDetailActivity.EXTRA_TASK_ID, task.getTaskId());
        startActivity(intent);
    }

    // ── ViewModel Observers ──────────────────────────────────────

    private void observeViewModel() {
        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        });

        viewModel.getOfficeTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null || tasks.isEmpty()) {
                recycler.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                recycler.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                adapter.submitList(tasks);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && getView() != null) {
                Snackbar.make(getView(), error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(requireContext().getColor(R.color.error))
                        .setTextColor(android.graphics.Color.WHITE)
                        .show();
            }
        });
    }
}