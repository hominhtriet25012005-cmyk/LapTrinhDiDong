package com.example.demo.ui.backup;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.R;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class BackupActivity extends AppCompatActivity {

    private TextView tvLastBackupTime;
    private MaterialButton btnBackupNow;
    private MaterialButton btnRestoreFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_backup);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tvLastBackupTime = findViewById(R.id.tvLastBackupTime);
        btnBackupNow = findViewById(R.id.btnBackupNow);
        btnRestoreFile = findViewById(R.id.btnRestoreFile);

        btnBackupNow.setOnClickListener(v -> {
            String currentTime = new SimpleDateFormat("dd/MM/yyyy · HH:mm", Locale.getDefault()).format(new Date());
            tvLastBackupTime.setText("Lần gần nhất: " + currentTime);
            Toast.makeText(this, "Đã tạo bản sao lưu thành công", Toast.LENGTH_SHORT).show();
        });

        btnRestoreFile.setOnClickListener(v -> showRestoreConfirmDialog());
    }

    private void showRestoreConfirmDialog() {
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
        tvMsg.setText(R.string.dialog_confirm_restore_desc);
        btnConfirm.setText(R.string.action_confirm);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            Toast.makeText(this, "Đã khôi phục dữ liệu thành công", Toast.LENGTH_SHORT).show();
        });

        dialog.show();
    }
}
