package com.phoenix.servicecall;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.phoenix.servicecall.models.OfficeContact;
import com.phoenix.servicecall.utils.SessionManager;
import com.phoenix.servicecall.viewmodels.ContactViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * ContactsActivity — office contacts management.
 *
 * Owner : view + add + edit + delete
 * Agent : view + add only
 *
 * Real-time sync via ContactViewModel Firestore listener.
 * Accessible from the toolbar admin icon (owner) or Profile tab (all).
 */
public class ContactsActivity extends AppCompatActivity {

    private RecyclerView            recycler;
    private LinearProgressIndicator progressBar;
    private View                    layoutEmpty;
    private TextInputEditText       inputSearch;
    private ContactAdapter          adapter;
    private ContactViewModel        viewModel;
    private SessionManager          session;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_contacts);

        session   = new SessionManager(this);
        viewModel = new ViewModelProvider(this).get(ContactViewModel.class);

        setupToolbar();
        setupRecyclerView();
        setupSearch();
        observeViewModel();
        viewModel.startListening();
    }

    // ── Toolbar ──────────────────────────────────────────────────

    private void setupToolbar() {
        androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        ImageButton btnAdd = toolbar.findViewById(R.id.action_add_contact);
        if (btnAdd != null) {
            btnAdd.setOnClickListener(v -> showAddContactSheet(null));
        }
    }

    // ── RecyclerView ─────────────────────────────────────────────

    private void setupRecyclerView() {
        recycler    = findViewById(R.id.recycler_contacts);
        progressBar = findViewById(R.id.progress_bar);
        layoutEmpty = findViewById(R.id.layout_empty);

        adapter = new ContactAdapter(
                session.isOwner(),
                contact -> showAddContactSheet(contact),   // edit
                contact -> confirmDelete(contact)           // delete
        );
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    // ── Search ───────────────────────────────────────────────────

    private void setupSearch() {
        inputSearch = findViewById(R.id.input_search);
        inputSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void afterTextChanged(Editable s) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                List<OfficeContact> filtered = viewModel.searchContacts(s.toString());
                adapter.submitList(filtered);
                layoutEmpty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
            }
        });
    }

    // ── Observers ────────────────────────────────────────────────

    private void observeViewModel() {
        viewModel.getIsLoading().observe(this, loading ->
                progressBar.setVisibility(loading ? View.VISIBLE : View.GONE));

        viewModel.getContacts().observe(this, contacts -> {
            if (contacts == null || contacts.isEmpty()) {
                recycler.setVisibility(View.GONE);
                layoutEmpty.setVisibility(View.VISIBLE);
            } else {
                recycler.setVisibility(View.VISIBLE);
                layoutEmpty.setVisibility(View.GONE);
                // Apply current search filter if any
                String query = inputSearch.getText() != null
                        ? inputSearch.getText().toString() : "";
                adapter.submitList(query.isEmpty() ? contacts
                        : viewModel.searchContacts(query));
            }
        });

        viewModel.getOperationResult().observe(this, result -> {
            if (result != null) {
                Snackbar.make(recycler, result, Snackbar.LENGTH_SHORT).show();
            }
        });

        viewModel.getErrorMessage().observe(this, error -> {
            if (error != null) {
                Snackbar.make(recycler, error, Snackbar.LENGTH_LONG)
                        .setBackgroundTint(getColor(R.color.error))
                        .setTextColor(android.graphics.Color.WHITE)
                        .show();
            }
        });
    }

    // ── Add / Edit Bottom Sheet ───────────────────────────────────

    /**
     * Shows a bottom sheet for adding a new contact (contact == null)
     * or editing an existing one (contact != null, owner only).
     */
    private void showAddContactSheet(@androidx.annotation.Nullable OfficeContact contact) {
        BottomSheetDialog sheet = new BottomSheetDialog(this);
        View sheetView = LayoutInflater.from(this)
                .inflate(R.layout.bottom_sheet_contact, null);
        sheet.setContentView(sheetView);

        TextInputLayout   layoutName  = sheetView.findViewById(R.id.layout_contact_name);
        TextInputLayout   layoutPhone = sheetView.findViewById(R.id.layout_contact_phone);
        TextInputEditText inputName   = sheetView.findViewById(R.id.input_contact_name);
        TextInputEditText inputPhone  = sheetView.findViewById(R.id.input_contact_phone);
        MaterialButton    btnSave     = sheetView.findViewById(R.id.btn_save_contact);
        TextView          tvTitle     = sheetView.findViewById(R.id.tv_sheet_title);

        boolean isEdit = (contact != null);
        tvTitle.setText(isEdit ? "Edit Contact" : "Add Office Contact");

        if (isEdit) {
            inputName.setText(contact.getName());
            inputPhone.setText(contact.getPhone());
        }

        btnSave.setOnClickListener(v -> {
            String name  = inputName.getText() != null
                    ? inputName.getText().toString().trim() : "";
            String phone = inputPhone.getText() != null
                    ? inputPhone.getText().toString().trim() : "";

            if (name.isEmpty()) {
                layoutName.setError("Name is required");
                return;
            }
            layoutName.setError(null);

            String normalized = OfficeContact.normalizePhone(phone);
            if (normalized.length() != 10) {
                layoutPhone.setError("Enter a valid 10-digit phone number");
                return;
            }
            layoutPhone.setError(null);

            if (isEdit) {
                viewModel.editContact(contact.getContactId(), name, phone);
            } else {
                viewModel.addContact(name, phone, session.getUid(), session.getName());
            }
            sheet.dismiss();
        });

        sheet.show();
    }

    // ── Delete Confirm ────────────────────────────────────────────

    private void confirmDelete(OfficeContact contact) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("Remove Contact")
                .setMessage("Remove \"" + contact.getName()
                        + "\" from office contacts? This cannot be undone.")
                .setPositiveButton("Remove", (d, w) ->
                        viewModel.deleteContact(contact.getContactId()))
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ════════════════════════════════════════════════════════════
    // INNER ADAPTER
    // ════════════════════════════════════════════════════════════

    static class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.VH> {

        interface OnEditListener  { void onEdit(OfficeContact contact);   }
        interface OnDeleteListener { void onDelete(OfficeContact contact); }

        private List<OfficeContact> items     = new ArrayList<>();
        private final boolean       isOwner;
        private final OnEditListener   editListener;
        private final OnDeleteListener deleteListener;

        ContactAdapter(boolean isOwner,
                       OnEditListener editListener,
                       OnDeleteListener deleteListener) {
            this.isOwner        = isOwner;
            this.editListener   = editListener;
            this.deleteListener = deleteListener;
        }

        void submitList(List<OfficeContact> list) {
            this.items = list != null ? list : new ArrayList<>();
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_contact, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            holder.bind(items.get(position), isOwner, editListener, deleteListener);
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView    tvAvatar, tvName, tvPhone, tvAddedBy;
            View        layoutOwnerActions;
            ImageButton btnEdit, btnDelete;

            VH(View v) {
                super(v);
                tvAvatar           = v.findViewById(R.id.tv_avatar);
                tvName             = v.findViewById(R.id.tv_name);
                tvPhone            = v.findViewById(R.id.tv_phone);
                tvAddedBy          = v.findViewById(R.id.tv_added_by);
                layoutOwnerActions = v.findViewById(R.id.layout_owner_actions);
                btnEdit            = v.findViewById(R.id.btn_edit);
                btnDelete          = v.findViewById(R.id.btn_delete);
            }

            void bind(OfficeContact contact, boolean isOwner,
                      OnEditListener editListener, OnDeleteListener deleteListener) {

                String name = contact.getName() != null ? contact.getName() : "?";
                tvName.setText(name);
                tvPhone.setText(contact.getPhone());
                tvAddedBy.setText("Added by " + (contact.getAddedByName() != null
                        ? contact.getAddedByName() : "unknown"));

                // Avatar initial
                tvAvatar.setText(name.isEmpty() ? "?" :
                        String.valueOf(name.charAt(0)).toUpperCase());

                // Owner-only edit/delete buttons
                layoutOwnerActions.setVisibility(isOwner ? View.VISIBLE : View.GONE);
                if (isOwner) {
                    btnEdit.setOnClickListener(v   -> editListener.onEdit(contact));
                    btnDelete.setOnClickListener(v -> deleteListener.onDelete(contact));
                }
            }
        }
    }
}