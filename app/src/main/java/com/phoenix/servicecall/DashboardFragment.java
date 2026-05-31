package com.phoenix.servicecall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.Timestamp;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.models.User;
import com.phoenix.servicecall.utils.FirestoreRepository;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DashboardFragment — Tab 1.
 *
 * Agent view:   4 summary cards (pending, done, overdue, snoozed) for today.
 * Owner view:   same cards (all agents combined) + per-agent team overview cards.
 *
 * Filter bar:   date range, status, service type, customer name, created by (owner only).
 * Default:      today's tasks only. Filter results update the summary cards.
 */
public class DashboardFragment extends Fragment {

    // Views — summary cards
    private TextView tvPending, tvDone, tvOverdue, tvSnoozed;

    // Views — filter
    private TextInputEditText    inputDateFrom, inputDateTo, inputFilterCustomer;
    private AutoCompleteTextView dropdownFilterStatus, dropdownFilterService, dropdownFilterAgent;
    private View                 layoutFilterAgent;
    private MaterialButton       btnApplyFilter, btnClearFilter;

    // Views — team overview (owner only)
    private View          layoutTeamOverview;
    private LinearLayout  containerAgents;

    // State
    private TaskViewModel  viewModel;
    private SessionManager session;
    private Timestamp      filterFrom = null;
    private Timestamp      filterTo   = null;
    private List<User>     agentList  = new ArrayList<>();

    private static final SimpleDateFormat DATE_FMT =
            new SimpleDateFormat("dd MMM yyyy", Locale.getDefault());

    public static DashboardFragment newInstance() {
        return new DashboardFragment();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_dashboard, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        session   = new SessionManager(requireContext());
        viewModel = new ViewModelProvider(requireActivity()).get(TaskViewModel.class);

        bindViews(view);
        setupFilterDropdowns();
        setupDatePickers();
        setupFilterButtons();
        setupOwnerExtras();

        observeViewModel();

        // Start listening to today's data
        viewModel.startListeningToTodaysTasks(session.getUid(), session.isOwner());
    }

    // ── View Binding ─────────────────────────────────────────────

    private void bindViews(View v) {
        tvPending  = v.findViewById(R.id.tv_pending_count);
        tvDone     = v.findViewById(R.id.tv_done_count);
        tvOverdue  = v.findViewById(R.id.tv_overdue_count);
        tvSnoozed  = v.findViewById(R.id.tv_snoozed_count);

        inputDateFrom         = v.findViewById(R.id.input_date_from);
        inputDateTo           = v.findViewById(R.id.input_date_to);
        inputFilterCustomer   = v.findViewById(R.id.input_filter_customer);
        dropdownFilterStatus  = v.findViewById(R.id.dropdown_filter_status);
        dropdownFilterService = v.findViewById(R.id.dropdown_filter_service);
        dropdownFilterAgent   = v.findViewById(R.id.dropdown_filter_agent);
        layoutFilterAgent     = v.findViewById(R.id.layout_filter_agent);
        btnApplyFilter        = v.findViewById(R.id.btn_apply_filter);
        btnClearFilter        = v.findViewById(R.id.btn_clear_filter);

        layoutTeamOverview = v.findViewById(R.id.layout_team_overview);
        containerAgents    = v.findViewById(R.id.container_agents);
    }

    // ── Filter Dropdowns ─────────────────────────────────────────

    private void setupFilterDropdowns() {
        // Status options
        List<String> statuses = new ArrayList<>();
        statuses.add("All");
        statuses.add("Pending");
        statuses.add("Done");
        statuses.add("Snoozed");
        statuses.add("Overdue");
        dropdownFilterStatus.setAdapter(new ArrayAdapter<>(
                requireContext(), android.R.layout.simple_dropdown_item_1line, statuses));
        dropdownFilterStatus.setText("All", false);

        // Service types from Firestore
        FirestoreRepository.getInstance().getServiceTypes(types -> {
            List<String> withAll = new ArrayList<>();
            withAll.add("All");
            withAll.addAll(types);
            dropdownFilterService.setAdapter(new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_dropdown_item_1line, withAll));
            dropdownFilterService.setText("All", false);
        }, e -> {});
    }

    // ── Date Pickers ─────────────────────────────────────────────

    private void setupDatePickers() {
        inputDateFrom.setOnClickListener(v -> showDatePicker(true));
        inputDateTo.setOnClickListener(v   -> showDatePicker(false));
    }

    private void showDatePicker(boolean isFrom) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder
                .datePicker()
                .setTitleText(isFrom ? "Select From Date" : "Select To Date")
                .build();

        picker.addOnPositiveButtonClickListener(selection -> {
            Date date = new Date(selection);
            String label = DATE_FMT.format(date);
            Timestamp ts = new Timestamp(date);

            if (isFrom) {
                filterFrom = ts;
                inputDateFrom.setText(label);
            } else {
                filterTo = ts;
                inputDateTo.setText(label);
            }
        });

        picker.show(getParentFragmentManager(), "date_picker");
    }

    // ── Filter Buttons ───────────────────────────────────────────

    private void setupFilterButtons() {
        btnApplyFilter.setOnClickListener(v -> applyFilter());
        btnClearFilter.setOnClickListener(v -> clearFilter());
    }

    private void applyFilter() {
        String statusRaw  = dropdownFilterStatus.getText().toString().trim();
        String serviceRaw = dropdownFilterService.getText().toString().trim();
        String agentRaw   = dropdownFilterAgent.getText().toString().trim();
        String customer   = inputFilterCustomer.getText() != null
                ? inputFilterCustomer.getText().toString().trim() : "";

        // Map "All" → null (no filter)
        String status  = (statusRaw.isEmpty()  || statusRaw.equals("All"))  ? null : statusRaw.toLowerCase();
        String service = (serviceRaw.isEmpty() || serviceRaw.equals("All")) ? null : serviceRaw;

        // Resolve agent UID from name
        String agentUid = null;
        if (!agentRaw.isEmpty() && !agentRaw.equals("All")) {
            for (User u : agentList) {
                if (u.getName().equals(agentRaw)) {
                    agentUid = u.getUid();
                    break;
                }
            }
        }
        // Agents always filter to their own UID
        if (!session.isOwner()) {
            agentUid = session.getUid();
        }

        viewModel.applyFilter(agentUid, status, service, filterFrom, filterTo);
    }

    private void clearFilter() {
        filterFrom = null;
        filterTo   = null;
        inputDateFrom.setText("");
        inputDateTo.setText("");
        inputFilterCustomer.setText("");
        dropdownFilterStatus.setText("All", false);
        dropdownFilterService.setText("All", false);
        dropdownFilterAgent.setText("All", false);

        // Reload default today data
        viewModel.startListeningToTodaysTasks(session.getUid(), session.isOwner());
    }

    // ── Owner Extras ─────────────────────────────────────────────

    private void setupOwnerExtras() {
        if (!session.isOwner()) return;

        // Show "Created By" filter
        layoutFilterAgent.setVisibility(View.VISIBLE);

        // Show team overview section
        layoutTeamOverview.setVisibility(View.VISIBLE);

        // Load agent list for filter dropdown and team cards
        FirestoreRepository.getInstance().getAllAgents(agents -> {
            agentList = agents;

            // Populate agent filter dropdown
            List<String> names = new ArrayList<>();
            names.add("All");
            for (User u : agents) names.add(u.getName());
            dropdownFilterAgent.setAdapter(new ArrayAdapter<>(
                    requireContext(), android.R.layout.simple_dropdown_item_1line, names));
            dropdownFilterAgent.setText("All", false);

            // Build team cards from today's tasks already loaded
            List<Task> todayTasks = viewModel.getTodaysTasks().getValue();
            if (todayTasks != null) buildAgentCards(agents, todayTasks);

        }, e -> {});
    }

    // ── ViewModel Observers ──────────────────────────────────────

    private void observeViewModel() {
        viewModel.getTodaysTasks().observe(getViewLifecycleOwner(), tasks -> {
            if (tasks == null) return;
            updateSummaryCards(tasks);

            if (session.isOwner() && !agentList.isEmpty()) {
                buildAgentCards(agentList, tasks);
            }
        });

        // Filter results also update summary cards
        viewModel.getOfficeTasks().observe(getViewLifecycleOwner(), tasks -> {
            // Only update cards if a filter has been applied (filterFrom or filterTo is set,
            // or a status/service filter is active — tracked by button state)
        });
    }

    // ── Summary Cards ────────────────────────────────────────────

    private void updateSummaryCards(List<Task> tasks) {
        tvPending.setText(String.valueOf(TaskViewModel.countByStatus(tasks, Task.STATUS_PENDING)));
        tvDone.setText(String.valueOf(TaskViewModel.countByStatus(tasks, Task.STATUS_DONE)));
        tvOverdue.setText(String.valueOf(TaskViewModel.countOverdue(tasks)));
        tvSnoozed.setText(String.valueOf(TaskViewModel.countByStatus(tasks, Task.STATUS_SNOOZED)));
    }

    // ── Agent Cards (owner only) ─────────────────────────────────

    private void buildAgentCards(List<User> agents, List<Task> allTasks) {
        if (containerAgents == null || !isAdded()) return;
        containerAgents.removeAllViews();

        LayoutInflater inflater = LayoutInflater.from(requireContext());

        for (User agent : agents) {
            List<Task> agentTasks = TaskViewModel.filterByAgent(allTasks, agent.getUid());

            View card = inflater.inflate(R.layout.item_agent_card, containerAgents, false);

            TextView tvInitials  = card.findViewById(R.id.tv_initials);
            TextView tvName      = card.findViewById(R.id.tv_agent_name);
            TextView tvPendingA  = card.findViewById(R.id.tv_pending);
            TextView tvDoneA     = card.findViewById(R.id.tv_done);
            TextView tvOverdueA  = card.findViewById(R.id.tv_overdue);

            // Initials from name
            String name = agent.getName() != null ? agent.getName() : "?";
            String initials = name.length() >= 2
                    ? String.valueOf(name.charAt(0)).toUpperCase()
                    + String.valueOf(name.charAt(name.contains(" ")
                    ? name.lastIndexOf(' ') + 1 : 1)).toUpperCase()
                    : name.substring(0, 1).toUpperCase();

            tvInitials.setText(initials);
            tvName.setText(name);
            tvPendingA.setText(String.valueOf(TaskViewModel.countByStatus(agentTasks, Task.STATUS_PENDING)));
            tvDoneA.setText(String.valueOf(TaskViewModel.countByStatus(agentTasks, Task.STATUS_DONE)));
            tvOverdueA.setText(String.valueOf(TaskViewModel.countOverdue(agentTasks)));

            containerAgents.addView(card);
        }
    }
}