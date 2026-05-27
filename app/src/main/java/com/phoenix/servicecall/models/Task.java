package com.phoenix.servicecall.models;

import com.google.firebase.Timestamp;

/**
 * Task model — mirrors the Firestore "tasks" collection.
 *
 * Status flow:
 *   pending → snoozed → pending → done
 *   pending/snoozed → deletion_requested → (approved: isDeleted=true | rejected: previousStatus)
 *
 * callType:
 *   "office"   — shared pool, visible to all agents and owner
 *   "personal" — private, visible only to creator
 */
public class Task {

    // ── Status Constants ─────────────────────────────────────────
    public static final String COLLECTION            = "tasks";

    public static final String STATUS_PENDING            = "pending";
    public static final String STATUS_SNOOZED            = "snoozed";
    public static final String STATUS_DONE               = "done";
    public static final String STATUS_DELETION_REQUESTED = "deletion_requested";

    public static final String TYPE_OFFICE   = "office";
    public static final String TYPE_PERSONAL = "personal";

    // ── Fields ───────────────────────────────────────────────────
    private String    taskId;
    private String    createdByUid;
    private String    createdByName;
    private String    customerName;
    private String    customerPhone;       // Normalized 10-digit
    private String    serviceType;
    private String    description;         // Optional, max 500 chars
    private String    status;              // See STATUS_ constants above
    private String    previousStatus;      // Stores status before deletion_requested
    private String    callType;            // office | personal
    private Timestamp createdAt;
    private Timestamp completedAt;         // null until marked done
    private Timestamp snoozedUntil;        // null unless snoozed
    private Timestamp reminderSentAt;      // null until first reminder fires
    private boolean   isDeleted;           // Soft delete — excluded from all views

    // ── Constructors ─────────────────────────────────────────────

    /** Required empty constructor for Firestore deserialization */
    public Task() {}

    /** Constructor for creating a new task */
    public Task(String createdByUid, String createdByName,
                String customerName, String customerPhone,
                String serviceType, String description,
                String callType, Timestamp createdAt) {
        this.createdByUid  = createdByUid;
        this.createdByName = createdByName;
        this.customerName  = customerName;
        this.customerPhone = OfficeContact.normalizePhone(customerPhone);
        this.serviceType   = serviceType;
        this.description   = description != null ? description : "";
        this.callType      = callType;
        this.status        = STATUS_PENDING;
        this.previousStatus = "";
        this.createdAt     = createdAt;
        this.isDeleted     = false;
    }

    // ── Status Helpers ───────────────────────────────────────────

    public boolean isPending()           { return STATUS_PENDING.equals(status); }
    public boolean isSnoozed()           { return STATUS_SNOOZED.equals(status); }
    public boolean isDone()              { return STATUS_DONE.equals(status); }
    public boolean isDeletionRequested() { return STATUS_DELETION_REQUESTED.equals(status); }
    public boolean isOffice()            { return TYPE_OFFICE.equals(callType); }
    public boolean isPersonal()          { return TYPE_PERSONAL.equals(callType); }

    /**
     * Marks this task as deletion-requested.
     * Saves the current status so it can be restored if owner rejects.
     */
    public void requestDeletion() {
        if (!STATUS_DELETION_REQUESTED.equals(this.status)) {
            this.previousStatus = this.status;
            this.status = STATUS_DELETION_REQUESTED;
        }
    }

    /**
     * Reverts status back to previousStatus when owner rejects deletion.
     */
    public void revertDeletion() {
        if (previousStatus != null && !previousStatus.isEmpty()) {
            this.status = this.previousStatus;
            this.previousStatus = "";
        } else {
            this.status = STATUS_PENDING; // safe fallback
        }
    }

    // ── Overdue Check ────────────────────────────────────────────

    /**
     * A task is considered overdue if:
     *   - Status is pending AND
     *   - reminderSentAt is not null (15-min timer has fired)
     *
     * Phase 4 will refine this with snoozedUntil checks.
     */
    public boolean isOverdue() {
        return STATUS_PENDING.equals(status) && reminderSentAt != null;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getTaskId() { return taskId; }
    public void setTaskId(String taskId) { this.taskId = taskId; }

    public String getCreatedByUid() { return createdByUid; }
    public void setCreatedByUid(String uid) { this.createdByUid = uid; }

    public String getCreatedByName() { return createdByName; }
    public void setCreatedByName(String name) { this.createdByName = name; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String name) { this.customerName = name; }

    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String phone) {
        this.customerPhone = OfficeContact.normalizePhone(phone);
    }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String type) { this.serviceType = type; }

    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getPreviousStatus() { return previousStatus; }
    public void setPreviousStatus(String prev) { this.previousStatus = prev; }

    public String getCallType() { return callType; }
    public void setCallType(String type) { this.callType = type; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp ts) { this.createdAt = ts; }

    public Timestamp getCompletedAt() { return completedAt; }
    public void setCompletedAt(Timestamp ts) { this.completedAt = ts; }

    public Timestamp getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(Timestamp ts) { this.snoozedUntil = ts; }

    public Timestamp getReminderSentAt() { return reminderSentAt; }
    public void setReminderSentAt(Timestamp ts) { this.reminderSentAt = ts; }

    public boolean isDeleted() { return isDeleted; }
    public void setDeleted(boolean deleted) { this.isDeleted = deleted; }
}