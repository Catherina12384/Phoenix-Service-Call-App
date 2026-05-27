package com.phoenix.servicecall.utils;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.phoenix.servicecall.models.User;

/**
 * FirestoreRepository — single access point for all Firestore reads/writes.
 *
 * Phase 1 covers:
 *   - Firestore initialisation with offline persistence
 *   - Fetch user by UID (for role-based routing after login)
 *   - Create user document on first login
 *
 * Phases 2–8 will add task, call, report, and settings operations here.
 */
public class FirestoreRepository {

    private static FirestoreRepository instance;
    private final FirebaseFirestore db;

    // ── Singleton ────────────────────────────────────────────────

    private FirestoreRepository() {
        db = FirebaseFirestore.getInstance();

        // Enable offline persistence — tasks work without internet
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .setCacheSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                .build();
        db.setFirestoreSettings(settings);
    }

    public static synchronized FirestoreRepository getInstance() {
        if (instance == null) {
            instance = new FirestoreRepository();
        }
        return instance;
    }

    // ── User Operations ──────────────────────────────────────────

    /**
     * Fetch a user document from Firestore by UID.
     * Called after Firebase Auth login to load role and profile.
     */
    public void getUser(String uid,
                        OnSuccessListener<User> onSuccess,
                        OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) {
                            user.setUid(snapshot.getId());
                        }
                        onSuccess.onSuccess(user);
                    } else {
                        // Document doesn't exist — new user
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Create or update a user document in Firestore.
     * Used on first login (owner pre-creates agent documents).
     */
    public void saveUser(User user,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Update the Telegram chat ID for the owner.
     * Called from SettingsActivity (Phase 9).
     */
    public void updateTelegramChatId(String uid, String chatId,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(uid)
                .update("telegramChatId", chatId)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Expose the raw FirebaseFirestore instance for operations
     * added in future phases.
     */
    public FirebaseFirestore getDb() {
        return db;
    }
}
