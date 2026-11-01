package com.example.demo.ui.settings;

import android.content.res.Configuration;
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
import com.example.demo.data.settings.AppSettings;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;

public class SettingsFragment extends Fragment {
    private static final int[] NIGHT_MODES = {
            AppCompatDelegate.MODE_NIGHT_NO,
            AppCompatDelegate.MODE_NIGHT_YES,
            AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
    };
    private static final int[] FIRST_DAYS = {1, 7, 6}; // Monday, Sunday, Saturday

    private MaterialSwitch switchDarkMode;
    private TextView tvDarkModeDesc;
    private TextView tvThemeDesc;
    private TextView tvStartDayValue;

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
        tvThemeDesc = view.findViewById(R.id.tvThemeDesc);
        tvStartDayValue = view.findViewById(R.id.tvStartDayValue);

        renderTheme();
        renderStartDay();

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            AppSettings.setNightMode(requireContext(), isChecked
                    ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            renderTheme();
        });

        view.findViewById(R.id.rowTheme).setOnClickListener(v -> showThemeDialog());
        view.findViewById(R.id.rowStartDay).setOnClickListener(v -> showStartDayDialog());

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
    }

    private boolean isDarkNow() {
        int mode = AppSettings.nightMode(requireContext());
        if (mode == AppCompatDelegate.MODE_NIGHT_YES) return true;
        if (mode == AppCompatDelegate.MODE_NIGHT_NO) return false;
        int flags = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        return flags == Configuration.UI_MODE_NIGHT_YES;
    }

    private void renderTheme() {
        boolean dark = isDarkNow();
        switchDarkMode.setChecked(dark);
        tvDarkModeDesc.setText(dark ? R.string.settings_dark_mode_active : R.string.settings_light_mode_active);
        int mode = AppSettings.nightMode(requireContext());
        tvThemeDesc.setText(mode == AppCompatDelegate.MODE_NIGHT_YES ? R.string.settings_theme_dark
                : mode == AppCompatDelegate.MODE_NIGHT_NO ? R.string.settings_theme_light
                : R.string.settings_theme_system);
    }

    private void renderStartDay() {
        tvStartDayValue.setText(dayName(AppSettings.firstDayOfWeek(requireContext())));
    }

    private int dayName(int isoDay) {
        return isoDay == 7 ? R.string.settings_day_sunday
                : isoDay == 6 ? R.string.settings_day_saturday : R.string.settings_day_monday;
    }

    private void showThemeDialog() {
        int current = AppSettings.nightMode(requireContext());
        int checked = 2;
        for (int i = 0; i < NIGHT_MODES.length; i++) {
            if (NIGHT_MODES[i] == current) checked = i;
        }
        String[] labels = {
                getString(R.string.settings_theme_light),
                getString(R.string.settings_theme_dark),
                getString(R.string.settings_theme_system)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_theme)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    AppSettings.setNightMode(requireContext(), NIGHT_MODES[which]);
                    if (isAdded() && getView() != null) renderTheme();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showStartDayDialog() {
        int current = AppSettings.firstDayOfWeek(requireContext());
        int checked = 0;
        for (int i = 0; i < FIRST_DAYS.length; i++) {
            if (FIRST_DAYS[i] == current) checked = i;
        }
        String[] labels = {
                getString(R.string.settings_day_monday),
                getString(R.string.settings_day_sunday),
                getString(R.string.settings_day_saturday)
        };
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.settings_start_day)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    dialog.dismiss();
                    AppSettings.setFirstDayOfWeek(requireContext(), FIRST_DAYS[which]);
                    if (isAdded() && getView() != null) renderStartDay();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}