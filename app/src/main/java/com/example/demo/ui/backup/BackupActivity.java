package com.example.demo.ui.backup;

import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.R;
import com.example.demo.data.backup.BackupData;
import com.example.demo.data.backup.BackupFormatException;
import com.example.demo.data.backup.BackupManager;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private TextView tvLastBackupTime;
    private MaterialButton btnBackupNow;
    private MaterialButton btnRestoreFile;
    private boolean busy;

    private final ActivityResultLauncher<String> createDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("application/json"), this::onBackupTargetChosen);
    private final ActivityResultLauncher<String[]> openDocumentLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), this::onRestoreFileChosen);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvLastBackupTime = findViewById(R.id.tvLastBackupTime);
        btnBackupNow = findViewById(R.id.btnBackupNow);
        btnRestoreFile = findViewById(R.id.btnRestoreFile);
        showLastBackupTime();

        btnBackupNow.setOnClickListener(v -> {
            if (!busy) {
                String stamp = new SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(new Date());
                createDocumentLauncher.launch("unischedule_backup_" + stamp + ".json");
            }
        });
        // "*/*" because file pickers do not always label .json files as application/json.
        btnRestoreFile.setOnClickListener(v -> {
            if (!busy) {
                openDocumentLauncher.launch(new String[]{"*/*"});
            }
        });
    }

    private void showLastBackupTime() {
        long last = BackupManager.lastBackupMillis(this);
        if (last > 0) {
            tvLastBackupTime.setText(getString(R.string.backup_last_time_format, formatTime(last)));
        }
    }

    // ---- backup -------------------------------------------------------------------------

    private void onBackupTargetChosen(Uri uri) {
        if (uri == null) {
            return; // the user closed the picker
        }
        setBusy(true);
        new Thread(() -> {
            try {
                BackupManager.writeBackup(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    showLastBackupTime();
                    Toast.makeText(this, R.string.backup_done, Toast.LENGTH_SHORT).show();
                });
            } catch (IOException | RuntimeException exception) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(getString(R.string.backup_error_write));
                });
            }
        }, "backup-write").start();
    }

    // ---- restore ------------------------------------------------------------------------

    private void onRestoreFileChosen(Uri uri) {
        if (uri == null) {
            return;
        }
        setBusy(true);
        new Thread(() -> {
            try {
                BackupData data = BackupManager.readBackup(getApplicationContext(), uri);
                runOnUiThread(() -> {
                    setBusy(false);
                    showRestoreConfirmDialog(data);
                });
            } catch (BackupFormatException exception) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(exception.getMessage());
                });
            } catch (IOException | RuntimeException exception) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(getString(R.string.backup_error_read));
                });
            }
        }, "backup-read").start();
    }

    private void showRestoreConfirmDialog(BackupData data) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMsg = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm);

        tvTitle.setText(R.string.dialog_confirm_restore_title);
        tvMsg.setText(getString(R.string.backup_restore_confirm_message,
                data.createdAtMillis > 0 ? formatTime(data.createdAtMillis)
                        : getString(R.string.backup_created_unknown),
                data.semesters.size(), data.subjects.size(),
                data.schedules.size(), data.exceptions.size()));
        btnConfirm.setText(R.string.action_confirm);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            restoreNow(data);
        });

        dialog.show();
    }

    private void restoreNow(BackupData data) {
        setBusy(true);
        new Thread(() -> {
            try {
                BackupManager.restore(getApplicationContext(), data);
                runOnUiThread(() -> {
                    setBusy(false);
                    Toast.makeText(this, R.string.backup_restore_done, Toast.LENGTH_LONG).show();
                });
            } catch (RuntimeException exception) {
                runOnUiThread(() -> {
                    setBusy(false);
                    showError(getString(R.string.backup_error_restore));
                });
            }
        }, "backup-restore").start();
    }

    // ---- helpers ------------------------------------------------------------------------

    private void setBusy(boolean value) {
        busy = value;
        btnBackupNow.setEnabled(!value);
        btnRestoreFile.setEnabled(!value);
    }

    private void showError(String message) {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_error_title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(new Date(millis));
    }
}