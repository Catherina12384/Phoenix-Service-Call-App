package com.phoenix.servicecall.models;

import com.google.firebase.Timestamp;

/**
 * OfficeContact model — mirrors the Firestore "office_contacts" collection.
 *
 * Maintained by owner; synced to all agents in real-time.
 * Phone numbers are always stored as normalized 10-digit strings
 * (no +91, no leading 0, no spaces or dashes).
 *
 * Access rules:
 *   Owner : read + create + edit + delete
 *   Agent : read + create only
 */
public class OfficeContact {

    // ── Constants ────────────────────────────────────────────────
    public static final String COLLECTION = "office_contacts";

    // ── Fields ───────────────────────────────────────────────────
    private String    contactId;       // Firestore document ID (auto-generated)
    private String    name;            // Customer full name
    private String    phone;           // Normalized 10-digit number
    private String    addedByUid;      // UID of user who added this contact
    private String    addedByName;     // Display name of adder (denormalised)
    private Timestamp createdAt;       // Creation timestamp
    private boolean   isActive;        // Soft-disable without deleting

    // ── Constructors ─────────────────────────────────────────────

    /** Required empty constructor for Firestore deserialization */
    public OfficeContact() {}

    /** Constructor for creating a new office contact */
    public OfficeContact(String name, String phone,
                         String addedByUid, String addedByName,
                         Timestamp createdAt) {
        this.name        = name;
        this.phone       = normalizePhone(phone);
        this.addedByUid  = addedByUid;
        this.addedByName = addedByName;
        this.createdAt   = createdAt;
        this.isActive    = true;
    }

    // ── Phone Normalization ──────────────────────────────────────

    /**
     * Normalizes an Indian phone number to a plain 10-digit string.
     *
     * Handles:
     *   +91XXXXXXXXXX  → XXXXXXXXXX
     *   0XXXXXXXXXX    → XXXXXXXXXX
     *   91XXXXXXXXXX   → XXXXXXXXXX
     *   XXX-XXX-XXXX   → XXXXXXXXXX
     *   XXX XXXX XXX   → XXXXXXXXXX
     *
     * Always store and compare using this method.
     */
    public static String normalizePhone(String raw) {
        if (raw == null) return "";

        // Strip all non-digit characters (spaces, dashes, parentheses)
        String digits = raw.replaceAll("[^0-9]", "");

        // Strip country code
        if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        }
        // Strip leading 0
        if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }

        return digits;
    }

    /**
     * Returns true if the given raw phone number (after normalization)
     * matches this contact's stored phone number.
     */
    public boolean matchesPhone(String rawIncoming) {
        return phone != null && phone.equals(normalizePhone(rawIncoming));
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getContactId() { return contactId; }
    public void setContactId(String id) { this.contactId = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = normalizePhone(phone); }

    public String getAddedByUid() { return addedByUid; }
    public void setAddedByUid(String uid) { this.addedByUid = uid; }

    public String getAddedByName() { return addedByName; }
    public void setAddedByName(String name) { this.addedByName = name; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp ts) { this.createdAt = ts; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }
}