package com.phoenix.servicecall.models;

import com.google.firebase.Timestamp;

/**
 * User model — mirrors the Firestore "users" collection.
 *
 * Roles:
 *   "owner" — full access, admin panel, all agents' tasks, reports
 *   "agent" — own tasks only, personal calls private
 *
 * The owner is also an agent; role flag determines elevated access.
 */
public class User {

    // ── Constants ────────────────────────────────────────────────
    public static final String ROLE_OWNER = "owner";
    public static final String ROLE_AGENT = "agent";

    public static final String COLLECTION = "users";

    // ── Fields ───────────────────────────────────────────────────
    private String uid;
    private String name;
    private String email;
    private String role;            // "owner" | "agent"
    private String phone;
    private String telegramChatId;  // Only relevant for owner
    private Timestamp createdAt;
    private boolean isActive;

    // ── Constructors ─────────────────────────────────────────────

    /** Required empty constructor for Firestore deserialization */
    public User() {}

    /** Full constructor used when creating a new user document */
    public User(String uid, String name, String email, String role,
                String phone, Timestamp createdAt) {
        this.uid          = uid;
        this.name         = name;
        this.email        = email;
        this.role         = role;
        this.phone        = phone;
        this.telegramChatId = "";
        this.createdAt    = createdAt;
        this.isActive     = true;
    }

    // ── Helpers ──────────────────────────────────────────────────

    /** Returns true if this user has owner-level access */
    public boolean isOwner() {
        return ROLE_OWNER.equals(role);
    }

    /** Returns true if this user is an active agent (includes owner) */
    public boolean isActiveAgent() {
        return isActive;
    }

    // ── Getters & Setters ────────────────────────────────────────

    public String getUid() { return uid; }
    public void setUid(String uid) { this.uid = uid; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getTelegramChatId() { return telegramChatId; }
    public void setTelegramChatId(String telegramChatId) {
        this.telegramChatId = telegramChatId;
    }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
}
