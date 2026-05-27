package com.phoenix.servicecall.models;

import com.google.firebase.Timestamp;

/**
 * Task model — mirrors the Firestore "tasks" collection.
 *
 * Status flow:  pending → snoozed → pending → done
 * callType:     "office"  (shared pool, visible to owner)
 *               "personal" (private, visible only to creator)
 */
public class Task {

    // ── Constants ────────────────────────────────────────────────
    public static final String COLLECTION = "tasks";

    public static final String STATUS_PENDING  = "pending";
    public static final String STATUS_SNOOZED  = "snoozed";
    public static final String STATUS_DONE     = "done";

    public static final String TYPE_OFFICE   = "office";
    public static final String TYPE_PERSONAL = "personal";

    // ── Fields ───────────────────────────────────────────────────
    private String taskId;
    private String createdByUid;
    private String createdByName;
    private String customerName;
    private String customerPhone;
    private String serviceType;
    private String description;       // optional, max 500 chars
    private String status;            // pending | snoozed | done
    private String callType;          // office | personal
    private Timestamp createdAt;
    private Timestamp completedAt;    // null until marked done
    private Timestamp snoozedUntil;   // null unless snoozed
    private Timestamp reminderSentAt; // null until first reminder fires
    private boolean isDeleted;        // soft delete

    // ── Constructors ─────────────────────────────────────────────

    /** Required empty constructor for Firestore */
    public Task() {}

    /** Constructor for creating a new task */
    public Task(String createdByUid, String createdByName,
                String customerName, String customerPhone,
                String serviceType, String description,
                String callType, Timestamp createdAt) {
        this.createdByUid  = createdByUid;
        this.createdByName = createdByName;
        this.customerName  = customerName;
        this.customerPhone = customerPhone;
        this.serviceType   = serviceType;
        this.description   = description != null ? description : "";
        this.callType      = callType;
        this.status        = STATUS_PENDING;
        this.createdAt     = createdAt;
        this.isDeleted     = false;
    }

    // ── Helpers ──────────────────────────────────────────────────

    public boolean isPending()  { return STATUS_PENDING.equals(status); }
    public boolean isSnoozed()  { return STATUS_SNOOZED.equals(status); }
    public boolean isDone()     { return STATUS_DONE.equals(status); }
    public boolean isOffice()   { return TYPE_OFFICE.equals(callType); }
    public boolean isPersonal() { return TYPE_PERSONAL.equals(callType); }

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
    public void setCustomerPhone(String phone) { this.customerPhone = phone; }

    public String getServiceType() { return serviceType; }
    public void setServiceType(String type) { this.serviceType = type; }

    public String getDescription() { return description; }
    public void setDescription(String desc) { this.description = desc; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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
