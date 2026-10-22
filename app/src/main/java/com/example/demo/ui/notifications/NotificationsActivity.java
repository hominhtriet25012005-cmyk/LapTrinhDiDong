package com.example.demo.ui.notifications;

import android.Manifest;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.demo.R;
import com.example.demo.notifications.NotificationPreferences;
import com.example.demo.notifications.NotificationScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.materialswitch.MaterialSwitch;

public class NotificationsActivity extends AppCompatActivity {
    private static final String PERMISSION_PREFS = "notification_settings";
    private static final String KEY_PERMISSION_REQUESTED = "post_notifications_requested";

    private FrameLayout boxIconPermission;
    private ImageView ivPermissionIcon;
    private TextView tvPermissionTitle;
    private TextView tvPermissionDesc;
    private MaterialButton btnTogglePermission;
    private MaterialSwitch switchReminder24h;
    private MaterialSwitch switchDailySummary;
    private MaterialButton btnSummaryTime;
    private ActivityResultLauncher<String> permissionRequester;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        permissionRequester = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(), granted -> {
                    refreshPermissionStatus();
                    if (granted) {
                        NotificationScheduler.syncAll(getApplicationContext());
                        Toast.makeText(this, R.string.notification_permission_granted,
                                Toast.LENGTH_SHORT).show();
                    }
                });

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        boxIconPermission = findViewById(R.id.boxIconPermission);
        ivPermissionIcon = findViewById(R.id.ivPermissionIcon);
        tvPermissionTitle = findViewById(R.id.tvPermissionTitle);
        tvPermissionDesc = findViewById(R.id.tvPermissionDesc);
        btnTogglePermission = findViewById(R.id.btnTogglePermission);
        switchReminder24h = findViewById(R.id.switchReminder24h);
        switchDailySummary = findViewById(R.id.switchDailySummary);
        btnSummaryTime = findViewById(R.id.btnSummaryTime);

        NotificationScheduler.createNotificationChannel(this);
        switchReminder24h.setChecked(NotificationPreferences.remindersEnabled(this));
        switchDailySummary.setChecked(NotificationPreferences.dailySummaryEnabled(this));
        btnSummaryTime.setText(NotificationPreferences.formatSummaryTime(this));

        btnTogglePermission.setOnClickListener(v -> requestOrOpenNotificationSettings());
        findViewById(R.id.rowSummaryTime).setOnClickListener(v -> showTimePicker());
        btnSummaryTime.setOnClickListener(v -> showTimePicker());

        switchReminder24h.setOnCheckedChangeListener((button, checked) -> {
            NotificationScheduler.setRemindersEnabled(this, checked);
            Toast.makeText(this, checked ? R.string.notification_reminders_enabled
                    : R.string.notification_reminders_disabled, Toast.LENGTH_SHORT).show();
        });
        switchDailySummary.setOnCheckedChangeListener((button, checked) -> {
            NotificationScheduler.setDailySummaryEnabled(this, checked);
            Toast.makeText(this, checked ? R.string.notification_summary_enabled
                    : R.string.notification_summary_disabled, Toast.LENGTH_SHORT).show();
        });
        refreshPermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshPermissionStatus();
        NotificationScheduler.syncAll(getApplicationContext());
    }

    private void refreshPermissionStatus() {
        boolean allowed = NotificationScheduler.notificationsAllowed(this);
        boolean runtimePermissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        int color = ContextCompat.getColor(this, allowed ? R.color.tag_green : R.color.primary);
        boxIconPermission.setBackgroundResource(allowed
                ? R.drawable.bg_round_icon_green : R.drawable.bg_round_icon_blue);
        ivPermissionIcon.setColorFilter(color);
        tvPermissionTitle.setText(allowed ? R.string.notification_permission_granted
                : runtimePermissionGranted ? R.string.notification_disabled
                : R.string.notification_permission_denied);
        tvPermissionDesc.setText(allowed ? R.string.notification_permission_granted_desc
                : runtimePermissionGranted ? R.string.notification_disabled_desc
                : R.string.notification_permission_denied_desc);
        btnTogglePermission.setText(runtimePermissionGranted
                ? R.string.action_open_notification_settings : R.string.action_grant_permission);
    }

    private void requestOrOpenNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            boolean requestedBefore = getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE)
                    .getBoolean(KEY_PERMISSION_REQUESTED, false);
            if (requestedBefore && !shouldShowRequestPermissionRationale(
                    Manifest.permission.POST_NOTIFICATIONS)) {
                openAppNotificationSettings();
                return;
            }
            getSharedPreferences(PERMISSION_PREFS, MODE_PRIVATE).edit()
                    .putBoolean(KEY_PERMISSION_REQUESTED, true).apply();
            permissionRequester.launch(Manifest.permission.POST_NOTIFICATIONS);
            return;
        }

        openAppNotificationSettings();
    }

    private void openAppNotificationSettings() {
        Intent settingsIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName());
        try {
            startActivity(settingsIntent);
        } catch (RuntimeException exception) {
            Intent fallback = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", getPackageName(), null));
            startActivity(fallback);
        }
    }

    private void showTimePicker() {
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) -> {
            btnSummaryTime.setText(String.format(java.util.Locale.ROOT, "%02d:%02d",
                    hourOfDay, minute));
            NotificationScheduler.setDailySummaryTime(this, hourOfDay, minute);
        }, NotificationPreferences.summaryHour(this),
                NotificationPreferences.summaryMinute(this), true);
        dialog.show();
    }
}
