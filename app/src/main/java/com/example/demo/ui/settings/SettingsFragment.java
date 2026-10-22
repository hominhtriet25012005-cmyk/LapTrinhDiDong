package com.example.demo.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {

    private MaterialSwitch switchDarkMode;
    private TextView tvDarkModeDesc;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchDarkMode = view.findViewById(R.id.switchDarkMode);
        tvDarkModeDesc = view.findViewById(R.id.tvDarkModeDesc);

        boolean isDark = AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES;
        switchDarkMode.setChecked(isDark);
        updateDarkDesc(isDark);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateDarkDesc(isChecked);
            if (isChecked) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        // Navigation rows
        view.findViewById(R.id.rowNotifications).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openNotifications();
            }
        });

        view.findViewById(R.id.rowStats).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openStats();
            }
        });

        view.findViewById(R.id.rowExport).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openExport();
            }
        });

        view.findViewById(R.id.rowBackup).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openBackup();
            }
        });

        view.findViewById(R.id.rowStates).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openStates();
            }
        });
    }

    private void updateDarkDesc(boolean isDark) {
        if (isDark) {
            tvDarkModeDesc.setText(R.string.settings_dark_mode_active);
        } else {
            tvDarkModeDesc.setText(R.string.settings_light_mode_active);
        }
    }
}
