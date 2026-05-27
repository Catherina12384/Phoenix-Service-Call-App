package com.phoenix.servicecall.utils;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.PersistentCacheSettings;
import com.google.firebase.firestore.Query;
import com.phoenix.servicecall.models.OfficeContact;
import com.phoenix.servicecall.models.Task;
import com.phoenix.servicecall.models.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * FirestoreRepository — single access point for all Firestore reads/writes.
 *
 * Phase 1: User fetch + save, session check
 * Phase 2: Task CRUD, OfficeContact CRUD, deletion approval flow,
 *           service types fetch, dashboard stats
 */
public class FirestoreRepository {

    private static FirestoreRepository instance;
    private final FirebaseFirestore db;

    // ── Singleton ────────────────────────────────────────────────

    private FirestoreRepository() {
        db = FirebaseFirestore.getInstance();

        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(PersistentCacheSettings.newBuilder()
                        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                        .build())
                .build();
        db.setFirestoreSettings(settings);
    }

    public static synchronized FirestoreRepository getInstance() {
        if (instance == null) instance = new FirestoreRepository();
        return instance;
    }

    // ════════════════════════════════════════════════════════════
    // USER OPERATIONS (Phase 1 — unchanged)
    // ════════════════════════════════════════════════════════════

    public void getUser(String uid,
                        OnSuccessListener<User> onSuccess,
                        OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        User user = snapshot.toObject(User.class);
                        if (user != null) user.setUid(snapshot.getId());
                        onSuccess.onSuccess(user);
                    } else {
                        onSuccess.onSuccess(null);
                    }
                })
                .addOnFailureListener(onFailure);
    }

    public void saveUser(User user,
                         OnSuccessListener<Void> onSuccess,
                         OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    public void updateTelegramChatId(String uid, String chatId,
                                     OnSuccessListener<Void> onSuccess,
                                     OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .document(uid)
                .update("telegramChatId", chatId)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    // ════════════════════════════════════════════════════════════
    // TASK OPERATIONS
    // ════════════════════════════════════════════════════════════

    /**
     * Create a new task document in Firestore.
     * Returns the generated taskId via onSuccess.
     */
    public void createTask(Task task,
                           OnSuccessListener<String> onSuccess,
                           OnFailureListener onFailure) {
        db.collection(Task.COLLECTION)
                .add(task)
                .addOnSuccessListener(ref -> {
                    // Store the generated ID back into the document
                    ref.update("taskId", ref.getId());
                    onSuccess.onSuccess(ref.getId());
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Real-time listener for all office tasks (not deleted).
     * Owner receives all agents' tasks; agents receive all office tasks.
     * Sorted by createdAt descending — TaskViewModel handles status-based re-sort.
     *
     * Returns ListenerRegistration — caller must call .remove() in onCleared().
     */
    public ListenerRegistration listenToOfficeTasks(
            OnSuccessListener<List<Task>> onUpdate,
            OnFailureListener onFailure) {

        return db.collection(Task.COLLECTION)
                .whereEqualTo("callType", Task.TYPE_OFFICE)
                .whereEqualTo("isDeleted", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { onFailure.onFailure(e); return; }
                    if (snapshots == null) return;

                    List<Task> tasks = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Task t = doc.toObject(Task.class);
                        if (t != null) {
                            t.setTaskId(doc.getId());
                            tasks.add(t);
                        }
                    }
                    onUpdate.onSuccess(tasks);
                });
    }

    /**
     * Real-time listener for a specific agent's own tasks (all call types).
     * Used for the dashboard "my tasks" section.
     */
    public ListenerRegistration listenToMyTasks(
            String uid,
            OnSuccessListener<List<Task>> onUpdate,
            OnFailureListener onFailure) {

        return db.collection(Task.COLLECTION)
                .whereEqualTo("createdByUid", uid)
                .whereEqualTo("isDeleted", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { onFailure.onFailure(e); return; }
                    if (snapshots == null) return;

                    List<Task> tasks = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Task t = doc.toObject(Task.class);
                        if (t != null) {
                            t.setTaskId(doc.getId());
                            tasks.add(t);
                        }
                    }
                    onUpdate.onSuccess(tasks);
                });
    }

    /**
     * Real-time listener for today's tasks — used by dashboard summary cards.
     * Filters createdAt >= today 00:00 and < tomorrow 00:00.
     */
    public ListenerRegistration listenToTodaysTasks(
            String uid,
            boolean isOwner,
            OnSuccessListener<List<Task>> onUpdate,
            OnFailureListener onFailure) {

        // Build today's date range in IST
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        Timestamp startOfDay = new Timestamp(cal.getTime());

        cal.add(Calendar.DAY_OF_MONTH, 1);
        Timestamp endOfDay = new Timestamp(cal.getTime());

        Query query = db.collection(Task.COLLECTION)
                .whereEqualTo("isDeleted", false)
                .whereGreaterThanOrEqualTo("createdAt", startOfDay)
                .whereLessThan("createdAt", endOfDay);

        // Agents only see their own tasks on dashboard
        if (!isOwner) {
            query = query.whereEqualTo("createdByUid", uid);
        }

        return query.addSnapshotListener((snapshots, e) -> {
            if (e != null) { onFailure.onFailure(e); return; }
            if (snapshots == null) return;

            List<Task> tasks = new ArrayList<>();
            for (DocumentSnapshot doc : snapshots.getDocuments()) {
                Task t = doc.toObject(Task.class);
                if (t != null) {
                    t.setTaskId(doc.getId());
                    tasks.add(t);
                }
            }
            onUpdate.onSuccess(tasks);
        });
    }

    /**
     * Filtered task query — used by dashboard filter bar.
     * All parameters are optional (pass null to skip).
     */
    public void getFilteredTasks(String filterByUid,
                                 String filterByStatus,
                                 String filterByServiceType,
                                 Timestamp fromDate,
                                 Timestamp toDate,
                                 OnSuccessListener<List<Task>> onSuccess,
                                 OnFailureListener onFailure) {

        Query query = db.collection(Task.COLLECTION)
                .whereEqualTo("isDeleted", false)
                .whereEqualTo("callType", Task.TYPE_OFFICE);

        if (filterByUid != null)         query = query.whereEqualTo("createdByUid", filterByUid);
        if (filterByStatus != null)      query = query.whereEqualTo("status", filterByStatus);
        if (filterByServiceType != null) query = query.whereEqualTo("serviceType", filterByServiceType);
        if (fromDate != null)            query = query.whereGreaterThanOrEqualTo("createdAt", fromDate);
        if (toDate != null)              query = query.whereLessThanOrEqualTo("createdAt", toDate);

        query.orderBy("createdAt", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<Task> tasks = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Task t = doc.toObject(Task.class);
                        if (t != null) {
                            t.setTaskId(doc.getId());
                            tasks.add(t);
                        }
                    }
                    onSuccess.onSuccess(tasks);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Mark a task as Done.
     * Sets status = "done" and completedAt = now.
     */
    public void markTaskDone(String taskId,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Task.STATUS_DONE);
        updates.put("completedAt", Timestamp.now());

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Undo a mark-done — reverts status to pending and clears completedAt.
     */
    public void undoMarkDone(String taskId,
                             OnSuccessListener<Void> onSuccess,
                             OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Task.STATUS_PENDING);
        updates.put("completedAt", null);

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Request deletion of a task.
     * Stores current status as previousStatus, sets status = deletion_requested.
     */
    public void requestTaskDeletion(String taskId, String currentStatus,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", Task.STATUS_DELETION_REQUESTED);
        updates.put("previousStatus", currentStatus);

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Owner approves deletion — sets isDeleted = true.
     */
    public void approveTaskDeletion(String taskId,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("isDeleted", true);
        updates.put("status", Task.STATUS_DELETION_REQUESTED); // keep for audit

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Owner rejects deletion — reverts status to previousStatus.
     */
    public void rejectTaskDeletion(String taskId, String previousStatus,
                                   OnSuccessListener<Void> onSuccess,
                                   OnFailureListener onFailure) {
        String revertTo = (previousStatus != null && !previousStatus.isEmpty())
                ? previousStatus : Task.STATUS_PENDING;

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", revertTo);
        updates.put("previousStatus", "");

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Owner edits a task — only customerName, serviceType, description allowed.
     */
    public void updateTask(String taskId, String customerName,
                           String serviceType, String description,
                           OnSuccessListener<Void> onSuccess,
                           OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("customerName", customerName);
        updates.put("serviceType", serviceType);
        updates.put("description", description != null ? description : "");

        db.collection(Task.COLLECTION)
                .document(taskId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Real-time listener for all deletion-requested tasks.
     * Used by PendingDeletionsActivity (owner only).
     */
    public ListenerRegistration listenToDeletionRequests(
            OnSuccessListener<List<Task>> onUpdate,
            OnFailureListener onFailure) {

        return db.collection(Task.COLLECTION)
                .whereEqualTo("status", Task.STATUS_DELETION_REQUESTED)
                .whereEqualTo("isDeleted", false)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { onFailure.onFailure(e); return; }
                    if (snapshots == null) return;

                    List<Task> tasks = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Task t = doc.toObject(Task.class);
                        if (t != null) {
                            t.setTaskId(doc.getId());
                            tasks.add(t);
                        }
                    }
                    onUpdate.onSuccess(tasks);
                });
    }

    // ════════════════════════════════════════════════════════════
    // OFFICE CONTACT OPERATIONS
    // ════════════════════════════════════════════════════════════

    /**
     * Add a new office contact.
     * Phone is normalized before saving.
     */
    public void addOfficeContact(OfficeContact contact,
                                 OnSuccessListener<String> onSuccess,
                                 OnFailureListener onFailure) {
        db.collection(OfficeContact.COLLECTION)
                .add(contact)
                .addOnSuccessListener(ref -> {
                    ref.update("contactId", ref.getId());
                    onSuccess.onSuccess(ref.getId());
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Real-time listener for all active office contacts.
     * Used by ContactsActivity and Phase 3 call matcher.
     */
    public ListenerRegistration listenToOfficeContacts(
            OnSuccessListener<List<OfficeContact>> onUpdate,
            OnFailureListener onFailure) {

        return db.collection(OfficeContact.COLLECTION)
                .whereEqualTo("isActive", true)
                .orderBy("name", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) { onFailure.onFailure(e); return; }
                    if (snapshots == null) return;

                    List<OfficeContact> contacts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        OfficeContact c = doc.toObject(OfficeContact.class);
                        if (c != null) {
                            c.setContactId(doc.getId());
                            contacts.add(c);
                        }
                    }
                    onUpdate.onSuccess(contacts);
                });
    }

    /**
     * Owner updates contact name and/or phone.
     */
    public void updateOfficeContact(String contactId, String name, String phone,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("name", name);
        updates.put("phone", OfficeContact.normalizePhone(phone));

        db.collection(OfficeContact.COLLECTION)
                .document(contactId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Owner soft-deletes a contact (isActive = false).
     */
    public void deleteOfficeContact(String contactId,
                                    OnSuccessListener<Void> onSuccess,
                                    OnFailureListener onFailure) {
        db.collection(OfficeContact.COLLECTION)
                .document(contactId)
                .update("isActive", false)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * One-time fetch of all active contacts — used to seed the local
     * phone contact group in Phase 3.
     */
    public void getOfficeContactsOnce(OnSuccessListener<List<OfficeContact>> onSuccess,
                                      OnFailureListener onFailure) {
        db.collection(OfficeContact.COLLECTION)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<OfficeContact> contacts = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        OfficeContact c = doc.toObject(OfficeContact.class);
                        if (c != null) {
                            c.setContactId(doc.getId());
                            contacts.add(c);
                        }
                    }
                    onSuccess.onSuccess(contacts);
                })
                .addOnFailureListener(onFailure);
    }

    // ════════════════════════════════════════════════════════════
    // SERVICE TYPES (from settings doc)
    // ════════════════════════════════════════════════════════════

    /**
     * Fetch service types from Firestore settings doc.
     * Falls back to hardcoded defaults if doc doesn't exist yet.
     * Owner can add more in Phase 9 SettingsActivity.
     */
    public void getServiceTypes(OnSuccessListener<List<String>> onSuccess,
                                OnFailureListener onFailure) {
        db.collection("settings")
                .document("service_types")
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        Object raw = snapshot.get("types");
                        if (raw instanceof List) {
                            //noinspection unchecked
                            onSuccess.onSuccess((List<String>) raw);
                            return;
                        }
                    }
                    // Fallback to hardcoded defaults
                    onSuccess.onSuccess(getDefaultServiceTypes());
                })
                .addOnFailureListener(e -> {
                    // Network error — use defaults so app still works offline
                    onSuccess.onSuccess(getDefaultServiceTypes());
                });
    }

    /**
     * Hardcoded default service types.
     * Stored in Firestore on first owner login (Phase 9 SettingsActivity).
     */
    public static List<String> getDefaultServiceTypes() {
        List<String> types = new ArrayList<>();
        types.add("Toner Filling");
        types.add("Printer Service");
        types.add("Networking");
        types.add("Laptop Service");
        types.add("Desktop Service");
        types.add("CCTV Service");
        types.add("Sales");
        types.add("Consulting");
        types.add("Others");
        return types;
    }

    // ════════════════════════════════════════════════════════════
    // ALL AGENTS (for owner dashboard per-agent cards)
    // ════════════════════════════════════════════════════════════

    /**
     * Fetch all active agents — used to build per-agent dashboard cards.
     */
    public void getAllAgents(OnSuccessListener<List<User>> onSuccess,
                             OnFailureListener onFailure) {
        db.collection(User.COLLECTION)
                .whereEqualTo("isActive", true)
                .get()
                .addOnSuccessListener(snapshots -> {
                    List<User> users = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        User u = doc.toObject(User.class);
                        if (u != null) {
                            u.setUid(doc.getId());
                            users.add(u);
                        }
                    }
                    onSuccess.onSuccess(users);
                })
                .addOnFailureListener(onFailure);
    }

    // ── Raw DB access for future phases ─────────────────────────
    public FirebaseFirestore getDb() { return db; }
}