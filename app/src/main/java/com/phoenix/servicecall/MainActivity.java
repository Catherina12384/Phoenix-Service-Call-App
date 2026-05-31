package com.phoenix.servicecall;

import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationBarView;
import com.phoenix.servicecall.auth.LoginActivity;
import com.phoenix.servicecall.utils.FCMService;
import com.phoenix.servicecall.utils.SessionManager;

/**
 * MainActivity — the root container after login.
 *
 * Hosts:
 *   Tab 1 — Dashboard       (DashboardFragment — Phase 2)
 *   Tab 2 — Office Tasks    (OfficeTasksFragment — Phase 2)
 *   Tab 3 — My Calls        (PersonalCallsFragment — Phase 3)
 *   Tab 4 — AI Chat         (AIChatFragment — Phase 8)
 *   Tab 5 — Profile         (ProfileFragment — Phase 1, basic)
 *
 * Owner-only:
 *   Admin icon in toolbar → AdminPanelActivity (Phase 5)
 *
 * FAB (bottom-right) → CreateTaskActivity (Phase 2)
 *
 * Phase 1 delivers: navigation structure, placeholder fragments,
 * role-aware toolbar, notification channels setup.
 */
public class MainActivity extends AppCompatActivity
        implements NavigationBarView.OnItemSelectedListener {

    private BottomNavigationView bottomNav;
    private FloatingActionButton fab;
    private SessionManager session;

    // Fragment tags
    private static final String TAG_DASHBOARD = "dashboard";
    private static final String TAG_TASKS     = "office_tasks";
    private static final String TAG_CALLS     = "personal_calls";
    private static final String TAG_AI        = "ai_chat";
    private static final String TAG_PROFILE   = "profile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        session = new SessionManager(this);

        // Guard: if somehow landed here without a session, go to login
        if (!session.isLoggedIn()) {
            goToLogin();
            return;
        }

        // Create notification channels on first launch
        NotificationManager nm = getSystemService(NotificationManager.class);
        FCMService.createChannels(nm);

        setupToolbar();
        setupBottomNav();
        setupFab();

        // Load default tab
        if (savedInstanceState == null) {
            loadFragment(TAG_DASHBOARD);
            bottomNav.setSelectedItemId(R.id.nav_dashboard);
        }
    }

    // ── Toolbar ──────────────────────────────────────────────────

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Owner-only admin button
        View adminIcon = findViewById(R.id.action_admin);
        if (adminIcon != null) {
            adminIcon.setVisibility(session.isOwner() ? View.VISIBLE : View.GONE);
            adminIcon.setOnClickListener(v -> {
                // Show popup menu with admin options
                android.widget.PopupMenu popup = new android.widget.PopupMenu(this, v);
                popup.getMenu().add(0, 1, 0, "Pending Deletions");
                popup.getMenu().add(0, 2, 1, "Office Contacts");
                popup.setOnMenuItemClickListener(item -> {
                    if (item.getItemId() == 1) {
                        startActivity(new Intent(this, PendingDeletionsActivity.class));
                    } else if (item.getItemId() == 2) {
                        startActivity(new Intent(this, ContactsActivity.class));
                    }
                    return true;
                });
                popup.show();
            });
        }

    }

    // ── Bottom Navigation ────────────────────────────────────────

    private void setupBottomNav() {
        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(this);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_dashboard)      { loadFragment(TAG_DASHBOARD); return true; }
        if (id == R.id.nav_office_tasks)   { loadFragment(TAG_TASKS);     return true; }
        if (id == R.id.nav_personal_calls) { loadFragment(TAG_CALLS);     return true; }
        if (id == R.id.nav_ai_chat)        { loadFragment(TAG_AI);        return true; }
        if (id == R.id.nav_profile)        { loadFragment(TAG_PROFILE);   return true; }
        return false;
    }

    // ── FAB ──────────────────────────────────────────────────────

    private void setupFab() {
        fab = findViewById(R.id.fab_create_task);
        fab.setOnClickListener(v ->
                startActivity(new Intent(this, CreateTaskActivity.class)));
    }

    // ── Fragment Loading ─────────────────────────────────────────

    private void loadFragment(String tag) {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(tag);

        if (fragment == null) {
            fragment = createFragment(tag);
        }

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment, tag)
                .commit();
    }

    private Fragment createFragment(String tag) {
        switch (tag) {
            case TAG_DASHBOARD: return DashboardFragment.newInstance();
            case TAG_TASKS:     return OfficeTasksFragment.newInstance();
            case TAG_CALLS:     return new PlaceholderFragment("My Calls\n\nPhase 3 will show your\npersonal call log here.");
            case TAG_AI:        return new PlaceholderFragment("AI Assistant\n\nPhase 8 will activate\nGemini chat here.");
            case TAG_PROFILE:   return ProfileFragment.newInstance();
            default:            return new PlaceholderFragment("Coming soon.");
        }
    }

    // ── Navigation Helpers ───────────────────────────────────────

    private void goToLogin() {
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
