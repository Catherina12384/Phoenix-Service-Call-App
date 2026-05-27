package com.phoenix.servicecall.auth;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phoenix.servicecall.R;
import com.phoenix.servicecall.MainActivity;
import com.phoenix.servicecall.utils.SessionManager;

/**
 * LoginActivity — Email + password sign-in screen.
 *
 * Design:
 *   - App logo / branding at top
 *   - Email TextInputLayout (outlined, Material Design 3)
 *   - Password TextInputLayout with show/hide toggle
 *   - Sign In button (full-width, filled)
 *   - Error messages via Snackbar
 *   - Loading state disables inputs and shows progress indicator
 *
 * No self-registration — owner pre-creates agent accounts.
 */
public class LoginActivity extends AppCompatActivity {

    public AuthViewModel viewModel;
    private SessionManager session;

    // Views
    private TextInputLayout  emailLayout;
    private TextInputLayout  passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private MaterialButton   signInButton;
    private CircularProgressIndicator progressIndicator;
    private View rootView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Edge-to-edge layout
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_login);

        session   = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);



        bindViews();
        setupListeners();
        observeAuthState();
    }

    // ── View Binding ─────────────────────────────────────────────

    private void bindViews() {
        rootView           = findViewById(R.id.root_view);
        emailLayout        = findViewById(R.id.email_layout);
        passwordLayout     = findViewById(R.id.password_layout);
        emailInput         = findViewById(R.id.email_input);
        passwordInput      = findViewById(R.id.password_input);
        signInButton       = findViewById(R.id.sign_in_button);
        progressIndicator  = findViewById(R.id.progress_indicator);
    }

    // ── Event Listeners ──────────────────────────────────────────

    private void setupListeners() {
        // Sign in on button tap
        signInButton.setOnClickListener(v -> attemptLogin());

        // Sign in when user presses "Done" on keyboard
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin();
                return true;
            }
            return false;
        });

        // Clear field errors when user starts typing
        emailInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) emailLayout.setError(null);
        });
        passwordInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) passwordLayout.setError(null);
        });
    }

    // ── Login Logic ──────────────────────────────────────────────

    private void attemptLogin() {
        String email    = emailInput.getText() != null
                ? emailInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null
                ? passwordInput.getText().toString() : "";

        // Local validation
        boolean valid = true;
        if (email.isEmpty()) {
            emailLayout.setError("Email is required");
            valid = false;
        }
        if (password.isEmpty()) {
            passwordLayout.setError("Password is required");
            valid = false;
        }
        if (!valid) return;

        viewModel.signIn(email, password);
    }

    // ── Observer ─────────────────────────────────────────────────

    private void observeAuthState() {
        viewModel.getAuthState().observe(this, state -> {
            switch (state.status) {

                case LOADING:
                    setLoading(true);
                    break;

                case SUCCESS:
                    setLoading(false);
                    if (state.user != null) {
                        // Persist session locally
                        session.saveSession(
                                state.user.getUid(),
                                state.user.getName(),
                                state.user.getEmail(),
                                state.user.getRole(),
                                state.user.getPhone()
                        );
                        goToMain();
                    }
                    break;

                case ERROR:
                    setLoading(false);
                    showError(state.errorMessage);
                    break;

                case SIGNED_OUT:
                    setLoading(false);
                    break;
            }
        });
    }

    // ── UI State Helpers ─────────────────────────────────────────

    private void setLoading(boolean loading) {
        signInButton.setEnabled(!loading);
        emailInput.setEnabled(!loading);
        passwordInput.setEnabled(!loading);
        progressIndicator.setVisibility(loading ? View.VISIBLE : View.GONE);
        signInButton.setText(loading ? "" : getString(R.string.sign_in));
    }

    private void showError(String message) {
        if (message == null) return;
        Snackbar.make(rootView, message, Snackbar.LENGTH_LONG)
                .setBackgroundTint(getColor(R.color.error))
                .setTextColor(getColor(android.R.color.white))
                .show();
    }

    private void goToMain() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
