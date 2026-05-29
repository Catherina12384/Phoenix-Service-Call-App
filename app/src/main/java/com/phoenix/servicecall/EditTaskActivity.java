package com.phoenix.servicecall;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phoenix.servicecall.utils.FirestoreRepository;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

/**
 * EditTaskActivity — owner only.
 * Allows editing customerName, serviceType, description.
 * Phone and callType are locked after creation.
 *
 * Extras received:
 *   EXTRA_TASK_ID       — Firestore document ID
 *   EXTRA_CUSTOMER_NAME — current customer name
 *   EXTRA_SERVICE_TYPE  — current service type
 *   EXTRA_DESCRIPTION   — current description
 */
public class EditTaskActivity extends AppCompatActivity {

    public static final String EXTRA_TASK_ID       = "edit_task_id";
    public static final String EXTRA_CUSTOMER_NAME = "edit_customer_name";
    public static final String EXTRA_SERVICE_TYPE  = "edit_service_type";
    public static final String EXTRA_DESCRIPTION   = "edit_description";

    private TextInputLayout     layoutName, layoutService, layoutDesc;
    private TextInputEditText   inputName, inputDesc;
    private AutoCompleteTextView dropdownService;
    private MaterialButton      btnSave;
    private LinearProgressIndicator progress;

    private TaskViewModel  viewModel;
    private SessionManager session;
    private String         taskId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_edit_task);

        session   = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        taskId    = getIntent().getStringExtra(EXTRA_TASK_ID);

        // Security guard — only owner can reach this screen
        if (!session.isOwner()) {
            finish();
            return;
        }

        bindViews();
        setupToolbar();
        populatePrefills();
        loadServiceTypes();
        setupSaveButton();
        observeViewModel();
    }

    // ── View Binding ─────────────────────────────────────────────

    private void bindViews() {
        layoutName      = findViewById(R.id.layout_customer_name);
        layoutService   = findViewById(R.id.layout_service_type);
        layoutDesc      = findViewById(R.id.layout_description);
        inputName       = findViewById(R.id.input_customer_name);
        inputDesc       = findViewById(R.id.input_description);
        dropdownService = findViewById(R.id.dropdown_service_type);
        btnSave         = findViewById(R.id.btn_save);
        progress        = findViewById(R.id.progress);
    }

    // ── Toolbar ──────────────────────────────────────────────────

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());
    }

    // ── Pre-fill Current Values ──────────────────────────────────

    private void populatePrefills() {
        String name    = getIntent().getStringExtra(EXTRA_CUSTOMER_NAME);
        String service = getIntent().getStringExtra(EXTRA_SERVICE_TYPE);
        String desc    = getIntent().getStringExtra(EXTRA_DESCRIPTION);

        if (name    != null) inputName.setText(name);
        if (desc    != null) inputDesc.setText(desc);
        // service type is set after dropdown loads in loadServiceTypes()
    }

    // ── Service Types Dropdown ───────────────────────────────────

    private void loadServiceTypes() {
        String currentService = getIntent().getStringExtra(EXTRA_SERVICE_TYPE);

        FirestoreRepository.getInstance().getServiceTypes(
                types -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            types);
                    dropdownService.setAdapter(adapter);

                    // Pre-select current service type
                    if (currentService != null && !currentService.isEmpty()) {
                        dropdownService.setText(currentService, false);
                    } else if (!types.isEmpty()) {
                        dropdownService.setText(types.get(0), false);
                    }
                },
                e -> {
                    java.util.List<String> defaults = FirestoreRepository.getDefaultServiceTypes();
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            defaults);
                    dropdownService.setAdapter(adapter);
                    if (currentService != null && !currentService.isEmpty()) {
                        dropdownService.setText(currentService, false);
                    }
                });
    }

    // ── Save ─────────────────────────────────────────────────────

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        String name    = inputName.getText() != null
                ? inputName.getText().toString().trim() : "";
        String service = dropdownService.getText().toString().trim();
        String desc    = inputDesc.getText() != null
                ? inputDesc.getText().toString().trim() : "";

        boolean valid = true;
        if (name.isEmpty()) {
            layoutName.setError("Customer name is required");
            valid = false;
        } else {
            layoutName.setError(null);
        }
        if (service.isEmpty()) {
            layoutService.setError("Please select a service type");
            valid = false;
        } else {
            layoutService.setError(null);
        }
        if (!valid) return;

        if (service.isEmpty()) service = "Others";
        viewModel.updateTask(taskId, name, service, desc);
    }

    // ── Observer ─────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnSave.setEnabled(!loading);
        });

        viewModel.getOperationResult().observe(this, result -> {
            if ("Task updated successfully".equals(result)) {
                setResult(RESULT_OK);
                finish();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Snackbar.make(btnSave, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.error))
                        .setTextColor(android.graphics.Color.WHITE)
                        .show();
            }
        });
    }
}