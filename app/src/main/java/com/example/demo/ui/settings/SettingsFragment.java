package com.example.demo.ui.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.settings.AppSettings;
import com.example.demo.settings.WeekSettings;
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

        boolean isDark = AppSettings.isDarkModeEnabled(requireContext());
        switchDarkMode.setChecked(isDark);
        updateDarkDesc(isDark);

        switchDarkMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateDarkDesc(isChecked);
            AppSettings.setDarkModeEnabled(requireContext(), isChecked);
        });

        // Navigation rows
        TextView tvStartDayVal = view.findViewById(R.id.tvStartDayVal);
        String[] daysOfWeekOptions = new String[]{"Thứ Hai", "Chủ Nhật"};
        int firstDayOfWeek = WeekSettings.getFirstDayOfWeek(requireContext());
        if (tvStartDayVal != null) {
            tvStartDayVal.setText(firstDayOfWeek == WeekSettings.SUNDAY
                    ? daysOfWeekOptions[1] : daysOfWeekOptions[0]);
        }
        view.findViewById(R.id.rowStartDay).setOnClickListener(v -> {
            int checkedItem = WeekSettings.getFirstDayOfWeek(requireContext())
                    == WeekSettings.SUNDAY ? 1 : 0;
            new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                    .setTitle(R.string.settings_start_day)
                    .setSingleChoiceItems(daysOfWeekOptions, checkedItem, (dialog, which) -> {
                        int selectedFirstDay = which == 1
                                ? WeekSettings.SUNDAY : WeekSettings.MONDAY;
                        WeekSettings.setFirstDayOfWeek(requireContext(), selectedFirstDay);
                        if (tvStartDayVal != null) {
                            tvStartDayVal.setText(daysOfWeekOptions[which]);
                        }
                        android.widget.Toast.makeText(requireContext(),
                                "Đã đổi ngày đầu tuần: " + daysOfWeekOptions[which],
                                android.widget.Toast.LENGTH_SHORT).show();
                        dialog.dismiss();
                    })
                    .setNegativeButton(R.string.action_cancel, null)
                    .show();
        });

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
