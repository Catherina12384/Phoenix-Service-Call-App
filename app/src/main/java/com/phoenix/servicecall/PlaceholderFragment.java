package com.phoenix.servicecall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * PlaceholderFragment — shown for tabs not yet implemented.
 * Replaced phase by phase with real fragments.
 */
public class PlaceholderFragment extends Fragment {

    private static final String ARG_TEXT = "text";

    public static PlaceholderFragment newInstance(String text) {
        PlaceholderFragment f = new PlaceholderFragment();
        Bundle args = new Bundle();
        args.putString(ARG_TEXT, text);
        f.setArguments(args);
        return f;
    }

    public PlaceholderFragment() {
        // Required empty public constructor
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_placeholder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        TextView tv = view.findViewById(R.id.placeholder_text);
        String text = getArguments() != null
                ? getArguments().getString(ARG_TEXT, "Coming soon.") : "Coming soon.";
        if (tv != null) tv.setText(text);
    }
}
