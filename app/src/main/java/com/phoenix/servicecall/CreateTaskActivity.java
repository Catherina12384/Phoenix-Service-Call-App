package com.phoenix.servicecall;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.Timestamp;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.utils.FirestoreRepository;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.TaskViewModel;

import java.util.List;

/**
 * CreateTaskActivity — manually log a new task via the FAB.
 *
 * Pre-fills:
 *   - customerPhone if passed via Intent extra "prefill_phone"
 *   - customerName  if passed via Intent extra "prefill_name"
 *   (Phase 3 will pass these from the call detection bottom sheet)
 *
 * Call type defaults to Office; agent can switch to Personal.
 * Service types loaded from Firestore settings doc (with hardcoded fallback).
 */
public class CreateTaskActivity extends AppCompatActivity {

    // Intent extras (used by Phase 3 call detection)
    public static final String EXTRA_PREFILL_PHONE = "prefill_phone";
    public static final String EXTRA_PREFILL_NAME  = "prefill_name";

    // Views
    private TextInputLayout       layoutName, layoutPhone, layoutService, layoutDesc;
    private TextInputEditText     inputName, inputPhone, inputDesc;
    private AutoCompleteTextView  dropdownService;
    private MaterialButtonToggleGroup toggleCallType;
    private MaterialButton        btnOffice, btnPersonal, btnSave;
    private MaterialCardView      cardPersonalNotice;
    private LinearProgressIndicator progress;
    private View                  rootView;

    // State
    private TaskViewModel  viewModel;
    private SessionManager session;
    private String         selectedCallType = Task.TYPE_OFFICE; // default
    private List<String>   serviceTypes;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_create_task);

        session   = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(TaskViewModel.class);

        bindViews();
        setupToolbar();
        setupCallTypeToggle();
        loadServiceTypes();
        applyPrefills();
        setupSaveButton();
        observeViewModel();
    }

    // ── View Binding ─────────────────────────────────────────────

    private void bindViews() {
        rootView           = findViewById(android.R.id.content);
        layoutName         = findViewById(R.id.layout_customer_name);
        layoutPhone        = findViewById(R.id.layout_customer_phone);
        layoutService      = findViewById(R.id.layout_service_type);
        layoutDesc         = findViewById(R.id.layout_description);
        inputName          = findViewById(R.id.input_customer_name);
        inputPhone         = findViewById(R.id.input_customer_phone);
        inputDesc          = findViewById(R.id.input_description);
        dropdownService    = findViewById(R.id.dropdown_service_type);
        toggleCallType     = findViewById(R.id.toggle_call_type);
        btnOffice          = findViewById(R.id.btn_office);
        btnPersonal        = findViewById(R.id.btn_personal);
        btnSave            = findViewById(R.id.btn_save_task);
        cardPersonalNotice = findViewById(R.id.card_personal_notice);
        progress           = findViewById(R.id.progress);
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

    // ── Call Type Toggle ─────────────────────────────────────────

    private void setupCallTypeToggle() {
        // Default selection: Office
        toggleCallType.check(R.id.btn_office);

        toggleCallType.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            if (checkedId == R.id.btn_office) {
                selectedCallType = Task.TYPE_OFFICE;
                cardPersonalNotice.setVisibility(View.GONE);
            } else {
                selectedCallType = Task.TYPE_PERSONAL;
                cardPersonalNotice.setVisibility(View.VISIBLE);
            }
        });
    }

    // ── Service Types ────────────────────────────────────────────

    private void loadServiceTypes() {
        FirestoreRepository.getInstance().getServiceTypes(
                types -> {
                    serviceTypes = types;
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            types);
                    dropdownService.setAdapter(adapter);
                    // Default to first item
                    if (!types.isEmpty()) {
                        dropdownService.setText(types.get(0), false);
                    }
                },
                e -> {
                    // Fallback — still set hardcoded defaults
                    serviceTypes = FirestoreRepository.getDefaultServiceTypes();
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(
                            this,
                            android.R.layout.simple_dropdown_item_1line,
                            serviceTypes);
                    dropdownService.setAdapter(adapter);
                    if (!serviceTypes.isEmpty()) {
                        dropdownService.setText(serviceTypes.get(0), false);
                    }
                });
    }

    // ── Pre-fills from Phase 3 call detection ────────────────────

    private void applyPrefills() {
        String prefillPhone = getIntent().getStringExtra(EXTRA_PREFILL_PHONE);
        String prefillName  = getIntent().getStringExtra(EXTRA_PREFILL_NAME);

        if (prefillPhone != null && !prefillPhone.isEmpty()) {
            inputPhone.setText(prefillPhone);
        }
        if (prefillName != null && !prefillName.isEmpty()) {
            inputName.setText(prefillName);
        }
    }

    // ── Save Button ──────────────────────────────────────────────

    private void setupSaveButton() {
        btnSave.setOnClickListener(v -> attemptSave());
    }

    private void attemptSave() {
        if (!validate()) return;

        String name        = inputName.getText().toString().trim();
        String phone       = inputPhone.getText().toString().trim();
        String serviceType = dropdownService.getText().toString().trim();
        String description = inputDesc.getText() != null
                ? inputDesc.getText().toString().trim() : "";

        // Use "Others" if service type somehow empty
        if (serviceType.isEmpty()) serviceType = "Others";

        Task task = new Task(
                session.getUid(),
                session.getName(),
                name,
                phone,
                serviceType,
                description,
                selectedCallType,
                Timestamp.now()
        );

        viewModel.createTask(task);
    }

    // ── Validation ───────────────────────────────────────────────

    private boolean validate() {
        boolean valid = true;

        String name = inputName.getText() != null
                ? inputName.getText().toString().trim() : "";
        if (name.isEmpty()) {
            layoutName.setError("Customer name is required");
            valid = false;
        } else {
            layoutName.setError(null);
        }

        String phone = inputPhone.getText() != null
                ? inputPhone.getText().toString().trim() : "";
        String normalized = com.phoenix.servicecall.models.OfficeContact.normalizePhone(phone);
        if (phone.isEmpty()) {
            layoutPhone.setError("Customer phone is required");
            valid = false;
        } else if (normalized.length() != 10) {
            layoutPhone.setError("Enter a valid 10-digit phone number");
            valid = false;
        } else {
            layoutPhone.setError(null);
        }

        if (dropdownService.getText().toString().trim().isEmpty()) {
            layoutService.setError("Please select a service type");
            valid = false;
        } else {
            layoutService.setError(null);
        }

        return valid;
    }

    // ── ViewModel Observer ───────────────────────────────────────

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading -> {
            progress.setVisibility(loading ? View.VISIBLE : View.GONE);
            btnSave.setEnabled(!loading);
        });

        viewModel.getOperationResult().observe(this, result -> {
            if (result != null && result.equals("Task created successfully")) {
                setResult(RESULT_OK);
                finish();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Snackbar.make(rootView, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.error))
                        .setTextColor(getColor(android.R.color.white))
                        .show();
            }
        });
    }
}