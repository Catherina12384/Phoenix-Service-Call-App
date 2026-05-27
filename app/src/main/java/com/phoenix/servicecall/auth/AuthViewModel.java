package com.phoenix.servicecall.auth;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.phoenix.servicecall.models.User;
import com.phoenix.servicecall.utils.FirestoreRepository;

/**
 * AuthViewModel — manages authentication state for LoginActivity and SplashActivity.
 *
 * Responsibilities:
 *   1. Firebase email/password sign-in
 *   2. Load user Firestore document after auth
 *   3. Expose LiveData for UI to observe: loading, error, success
 *   4. Determine role (owner/agent) for routing
 */
public class AuthViewModel extends ViewModel {

    // ── LiveData exposed to UI ───────────────────────────────────
    private final MutableLiveData<AuthState> authState = new MutableLiveData<>();

    private final FirebaseAuth       firebaseAuth;
    private final FirestoreRepository repository;

    public AuthViewModel() {
        firebaseAuth = FirebaseAuth.getInstance();
        repository   = FirestoreRepository.getInstance();
    }

    // ── Public API ───────────────────────────────────────────────

    public LiveData<AuthState> getAuthState() { return authState; }

    /**
     * Returns the currently signed-in Firebase user, or null if signed out.
     */
    public FirebaseUser getCurrentFirebaseUser() {
        return firebaseAuth.getCurrentUser();
    }

    /**
     * Called from SplashActivity on launch.
     * If Firebase already has a session, loads the Firestore user document.
     * If not, emits SIGNED_OUT so Splash routes to LoginActivity.
     */
    public void checkExistingSession() {
        FirebaseUser firebaseUser = firebaseAuth.getCurrentUser();
        if (firebaseUser == null) {
            authState.setValue(AuthState.signedOut());
            return;
        }
        loadUserFromFirestore(firebaseUser.getUid());
    }

    /**
     * Sign in with email and password.
     * On success, loads Firestore user document to get role.
     */
    public void signIn(String email, String password) {
        if (email == null || email.trim().isEmpty()) {
            authState.setValue(AuthState.error("Please enter your email address"));
            return;
        }
        if (password == null || password.isEmpty()) {
            authState.setValue(AuthState.error("Please enter your password"));
            return;
        }

        authState.setValue(AuthState.loading());

        firebaseAuth.signInWithEmailAndPassword(email.trim(), password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user != null) {
                        loadUserFromFirestore(user.getUid());
                    } else {
                        authState.setValue(AuthState.error("Sign-in failed. Please try again."));
                    }
                })
                .addOnFailureListener(e -> {
                    String msg = friendlyAuthError(e.getMessage());
                    authState.setValue(AuthState.error(msg));
                });
    }

    /**
     * Sign out the current user and clear LiveData state.
     */
    public void signOut() {
        firebaseAuth.signOut();
        authState.setValue(AuthState.signedOut());
    }

    // ── Private Helpers ──────────────────────────────────────────

    private void loadUserFromFirestore(String uid) {
        repository.getUser(uid,
                user -> {
                    if (user != null) {
                        authState.setValue(AuthState.success(user));
                    } else {
                        // Authenticated but no Firestore doc — account setup incomplete
                        authState.setValue(
                                AuthState.error("Account not found. Contact your admin."));
                        firebaseAuth.signOut();
                    }
                },
                e -> authState.setValue(
                        AuthState.error("Failed to load account. Check your connection.")));
    }

    /** Convert Firebase auth error messages to user-friendly strings */
    private String friendlyAuthError(String raw) {
        if (raw == null) return "An unknown error occurred.";
        if (raw.contains("no user record"))    return "No account found with this email.";
        if (raw.contains("password is invalid") || raw.contains("INVALID_LOGIN_CREDENTIALS"))
            return "Incorrect password. Please try again.";
        if (raw.contains("badly formatted"))   return "Please enter a valid email address.";
        if (raw.contains("network error"))     return "No internet connection. Check your network.";
        if (raw.contains("too many requests")) return "Too many attempts. Please wait and try again.";
        return "Sign-in failed. Please check your credentials.";
    }

    // ── AuthState sealed-like class ──────────────────────────────

    public static class AuthState {
        public enum Status { LOADING, SUCCESS, ERROR, SIGNED_OUT }

        public final Status status;
        public final User user;
        public final String errorMessage;

        private AuthState(Status status, User user, String errorMessage) {
            this.status       = status;
            this.user         = user;
            this.errorMessage = errorMessage;
        }

        public static AuthState loading()           { return new AuthState(Status.LOADING,    null, null); }
        public static AuthState success(User user)  { return new AuthState(Status.SUCCESS,    user, null); }
        public static AuthState error(String msg)   { return new AuthState(Status.ERROR,      null, msg);  }
        public static AuthState signedOut()         { return new AuthState(Status.SIGNED_OUT, null, null); }
    }
}
