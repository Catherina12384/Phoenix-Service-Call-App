package com.phoenix.servicecall;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.firebase.auth.FirebaseAuth;
import com.phoenix.servicecall.auth.LoginActivity;
import com.phoenix.servicecall.utils.SessionManager;

/**
 * ProfileFragment — shows user info and logout button.
 * Phase 1 implementation: name, email, role badge, logout.
 */
public class ProfileFragment extends Fragment {

    public static ProfileFragment newInstance() {
        return new ProfileFragment();
    }

    public ProfileFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        SessionManager session = new SessionManager(requireContext());

        TextView nameText  = view.findViewById(R.id.tv_name);
        TextView emailText = view.findViewById(R.id.tv_email);
        TextView roleText  = view.findViewById(R.id.tv_role);
        MaterialButton logoutButton = view.findViewById(R.id.btn_logout);

        if (nameText  != null) nameText.setText(session.getName());
        if (emailText != null) emailText.setText(session.getEmail());
        if (roleText  != null) {
            String role = session.isOwner() ? "Owner" : "Service Agent";
            roleText.setText(role);
        }

        if (logoutButton != null) {
            logoutButton.setOnClickListener(v -> confirmLogout(session));
        }
    }

    private void confirmLogout(SessionManager session) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sign Out")
                .setMessage("Are you sure you want to sign out?")
                .setPositiveButton("Sign Out", (dialog, which) -> {
                    FirebaseAuth.getInstance().signOut();
                    session.clearSession();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
