package com.phoenix.servicecall.utils;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * SessionManager — persists lightweight user session data locally
 * (name, role, uid) so the app never shows a blank screen between
 * auth checks on launch.
 *
 * Firebase Auth handles the actual token; this is UI-layer cache only.
 */
public class SessionManager {

    private static final String PREF_NAME    = "ServiceCallSession";
    private static final String KEY_UID      = "uid";
    private static final String KEY_NAME     = "name";
    private static final String KEY_EMAIL    = "email";
    private static final String KEY_ROLE     = "role";
    private static final String KEY_PHONE    = "phone";
    private static final String KEY_LOGGED_IN = "is_logged_in";

    private final SharedPreferences prefs;
    private final SharedPreferences.Editor editor;

    public SessionManager(Context context) {
        prefs  = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    /** Save session after successful login */
    public void saveSession(String uid, String name, String email,
                            String role, String phone) {
        editor.putString(KEY_UID,       uid);
        editor.putString(KEY_NAME,      name);
        editor.putString(KEY_EMAIL,     email);
        editor.putString(KEY_ROLE,      role);
        editor.putString(KEY_PHONE,     phone);
        editor.putBoolean(KEY_LOGGED_IN, true);
        editor.apply();
    }

    /** Clear session on logout */
    public void clearSession() {
        editor.clear().apply();
    }

    public boolean isLoggedIn()  { return prefs.getBoolean(KEY_LOGGED_IN, false); }
    public String getUid()       { return prefs.getString(KEY_UID,   ""); }
    public String getName()      { return prefs.getString(KEY_NAME,  ""); }
    public String getEmail()     { return prefs.getString(KEY_EMAIL, ""); }
    public String getRole()      { return prefs.getString(KEY_ROLE,  ""); }
    public String getPhone()     { return prefs.getString(KEY_PHONE, ""); }

    public boolean isOwner() {
        return "owner".equals(getRole());
    }
}
