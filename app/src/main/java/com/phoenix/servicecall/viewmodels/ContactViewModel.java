package com.phoenix.servicecall.viewmodels;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.ListenerRegistration;
import com.phoenix.servicecall.models.OfficeContact;
import com.phoenix.servicecall.utils.FirestoreRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * ContactViewModel — manages office contacts state for:
 *   - ContactsActivity  (owner: full CRUD; agent: view + add)
 *   - Phase 3 call matcher (read-only, cached list)
 *
 * Real-time Firestore listener keeps the list in sync across all agents.
 * Listener is cleaned up in onCleared().
 */
public class ContactViewModel extends ViewModel {

    // ── LiveData ─────────────────────────────────────────────────
    private final MutableLiveData<List<OfficeContact>> contacts      = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String>              errorMessage  = new MutableLiveData<>();
    private final MutableLiveData<Boolean>             isLoading     = new MutableLiveData<>(false);
    private final MutableLiveData<String>              operationResult = new MutableLiveData<>();

    private ListenerRegistration contactsListener;
    private final FirestoreRepository repo = FirestoreRepository.getInstance();

    // ════════════════════════════════════════════════════════════
    // REAL-TIME LISTENER
    // ════════════════════════════════════════════════════════════

    /**
     * Start real-time listener for all active office contacts.
     * Call from ContactsActivity.onViewCreated().
     * Safe to call multiple times — only registers once.
     */
    public void startListening() {
        if (contactsListener != null) return;

        contactsListener = repo.listenToOfficeContacts(
                list -> contacts.postValue(list),
                e    -> errorMessage.postValue("Failed to load contacts: " + e.getMessage())
        );
    }

    public LiveData<List<OfficeContact>> getContacts() { return contacts; }

    // ════════════════════════════════════════════════════════════
    // SEARCH FILTER (client-side — list is small)
    // ════════════════════════════════════════════════════════════

    /**
     * Returns contacts whose name or phone contains the query string.
     * Case-insensitive. Used by the search bar in ContactsActivity.
     */
    public List<OfficeContact> searchContacts(String query) {
        List<OfficeContact> all = contacts.getValue();
        if (all == null || query == null || query.trim().isEmpty()) {
            return all != null ? all : new ArrayList<>();
        }

        String lower = query.toLowerCase().trim();
        List<OfficeContact> result = new ArrayList<>();
        for (OfficeContact c : all) {
            if ((c.getName() != null && c.getName().toLowerCase().contains(lower))
                    || (c.getPhone() != null && c.getPhone().contains(lower))) {
                result.add(c);
            }
        }
        return result;
    }

    // ════════════════════════════════════════════════════════════
    // ADD CONTACT (owner + agent)
    // ════════════════════════════════════════════════════════════

    public void addContact(String name, String phone,
                           String addedByUid, String addedByName) {

        // Validate
        if (name == null || name.trim().isEmpty()) {
            errorMessage.setValue("Contact name is required");
            return;
        }
        String normalized = OfficeContact.normalizePhone(phone);
        if (normalized.length() != 10) {
            errorMessage.setValue("Please enter a valid 10-digit phone number");
            return;
        }

        // Check for duplicate phone in current list
        List<OfficeContact> current = contacts.getValue();
        if (current != null) {
            for (OfficeContact c : current) {
                if (normalized.equals(c.getPhone())) {
                    errorMessage.setValue("This number is already in office contacts");
                    return;
                }
            }
        }

        isLoading.setValue(true);
        OfficeContact contact = new OfficeContact(
                name.trim(), phone, addedByUid, addedByName, Timestamp.now());

        repo.addOfficeContact(contact,
                id -> {
                    isLoading.postValue(false);
                    operationResult.postValue("Contact added successfully");
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue("Failed to add contact: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════════════
    // EDIT CONTACT (owner only)
    // ════════════════════════════════════════════════════════════

    public void editContact(String contactId, String name, String phone) {
        if (name == null || name.trim().isEmpty()) {
            errorMessage.setValue("Contact name is required");
            return;
        }
        String normalized = OfficeContact.normalizePhone(phone);
        if (normalized.length() != 10) {
            errorMessage.setValue("Please enter a valid 10-digit phone number");
            return;
        }

        isLoading.setValue(true);
        repo.updateOfficeContact(contactId, name.trim(), phone,
                v -> {
                    isLoading.postValue(false);
                    operationResult.postValue("Contact updated successfully");
                },
                e -> {
                    isLoading.postValue(false);
                    errorMessage.postValue("Failed to update contact: " + e.getMessage());
                });
    }

    // ════════════════════════════════════════════════════════════
    // DELETE CONTACT (owner only — soft delete)
    // ════════════════════════════════════════════════════════════

    public void deleteContact(String contactId) {
        repo.deleteOfficeContact(contactId,
                v -> operationResult.postValue("Contact removed"),
                e -> errorMessage.postValue("Failed to remove contact: " + e.getMessage()));
    }

    // ════════════════════════════════════════════════════════════
    // PHASE 3 HELPER — lookup by phone number
    // ════════════════════════════════════════════════════════════

    /**
     * Checks if an incoming phone number belongs to an office contact.
     * Returns the matching OfficeContact, or null if not found.
     * Used by Phase 3 call detector.
     */
    public OfficeContact findByPhone(String rawPhone) {
        List<OfficeContact> all = contacts.getValue();
        if (all == null) return null;
        for (OfficeContact c : all) {
            if (c.matchesPhone(rawPhone)) return c;
        }
        return null;
    }

    // ════════════════════════════════════════════════════════════
    // SHARED LIVEDATA
    // ════════════════════════════════════════════════════════════

    public LiveData<String>  getErrorMessage()    { return errorMessage; }
    public LiveData<Boolean> getIsLoading()        { return isLoading; }
    public LiveData<String>  getOperationResult() { return operationResult; }

    // ════════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════════

    @Override
    protected void onCleared() {
        super.onCleared();
        if (contactsListener != null) contactsListener.remove();
    }
}