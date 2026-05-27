package com.phoenix.servicecall.auth;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.splashscreen.SplashScreen;
import androidx.lifecycle.ViewModelProvider;

import com.phoenix.servicecall.R;
import com.phoenix.servicecall.MainActivity;
import com.phoenix.servicecall.utils.SessionManager;

/**
 * SplashActivity — app entry point.
 *
 * Flow:
 *   1. Show splash logo (via SplashScreen API — handled by the theme)
 *   2. Check Firebase Auth session
 *      ├── Session exists  → load Firestore user → route to MainActivity
 *      └── No session      → route to LoginActivity
 *
 * Uses Android 12+ SplashScreen API for a smooth native launch experience.
 * The actual logo/branding is defined in res/values/themes.xml.
 */
@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_MIN_DURATION_MS = 1200;

    private AuthViewModel viewModel;
    private boolean       isReadyToRoute = false;
    private boolean       minTimeElapsed = false;
    private AuthViewModel.AuthState pendingState = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Install splash screen BEFORE super.onCreate
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        viewModel = new ViewModelProvider(this).get(AuthViewModel.class);

        // Keep splash visible until we're ready to route
        splashScreen.setKeepOnScreenCondition(() -> !isReadyToRoute);

        // Enforce a minimum display time so the logo doesn't flash
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            minTimeElapsed = true;
            if (pendingState != null) {
                route(pendingState);
            }
        }, SPLASH_MIN_DURATION_MS);

        // Check if Firebase already has a logged-in session
        viewModel.checkExistingSession();

        // Observe result
        viewModel.getAuthState().observe(this, state -> {
            if (state.status == AuthViewModel.AuthState.Status.LOADING) {
                return; // still checking — keep splash up
            }
            if (minTimeElapsed) {
                route(state);
            } else {
                pendingState = state; // wait for min duration
            }
        });
    }

    private void route(AuthViewModel.AuthState state) {
        isReadyToRoute = true;

        if (state.status == AuthViewModel.AuthState.Status.SUCCESS && state.user != null) {
            // Save session locally for fast future access
            SessionManager session = new SessionManager(this);
            session.saveSession(
                    state.user.getUid(),
                    state.user.getName(),
                    state.user.getEmail(),
                    state.user.getRole(),
                    state.user.getPhone()
            );
            goToMain();
        } else {
            goToLogin();
        }
    }

    private void goToMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }

    private void goToLogin() {
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
