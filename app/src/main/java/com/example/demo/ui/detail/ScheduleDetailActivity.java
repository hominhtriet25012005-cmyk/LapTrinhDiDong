package com.example.demo.ui.detail;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.R;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.notifications.NotificationScheduler;
import com.example.demo.ui.form.ScheduleFormActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ScheduleDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";

    private AppRepository repository;
    private ScheduleEntity currentSchedule;
    private boolean deleting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.btnEditSchedule).setOnClickListener(v -> openEditForm());
        findViewById(R.id.btnDeleteSchedule).setOnClickListener(v -> showDeleteConfirmDialog());

        long scheduleId = getIntent().getLongExtra(EXTRA_SCHEDULE_ID, 0L);
        if (scheduleId <= 0) {
            showScheduleNotFound();
            return;
        }
        repository = AppRepository.getInstance(getApplicationContext());
        repository.observeScheduleDetails(scheduleId).observe(this, this::bindDetails);
    }

    private void bindDetails(ScheduleDetails details) {
        if (details == null) {
            if (!deleting) {
                showScheduleNotFound();
            }
            return;
        }
        if (details.dayOfWeek < 1 || details.dayOfWeek > 7) {
            showScheduleNotFound();
            return;
        }
        currentSchedule = new ScheduleEntity(details.classSectionId, details.dayOfWeek,
                details.startMinute, details.endMinute, details.room,
                details.validFromEpochDay, details.validToEpochDay, details.note);
        currentSchedule.id = details.scheduleId;
        currentSchedule.notificationEnabled = details.notificationEnabled;
        currentSchedule.reminderMinutesBefore = details.reminderMinutesBefore;

        ((TextView) findViewById(R.id.tvDetailSubjectCode)).setText(
                getString(R.string.schedule_detail_code_format, safe(details.subjectCode), safe(details.classCode)));
        ((TextView) findViewById(R.id.tvDetailSubjectName)).setText(safe(details.subjectName));
        String semester = details.semesterAcademicYear == null || details.semesterAcademicYear.trim().isEmpty()
                ? safe(details.semesterName)
                : getString(R.string.semester_label_format, details.semesterName, details.semesterAcademicYear);
        ((TextView) findViewById(R.id.tvDetailSemester)).setText(semester);
        ((TextView) findViewById(R.id.tvDetailDay)).setText(
                getResources().getStringArray(R.array.days_of_week)[details.dayOfWeek - 1]);
        ((TextView) findViewById(R.id.tvDetailTime)).setText(
                formatTime(details.startMinute) + " – " + formatTime(details.endMinute));
        ((TextView) findViewById(R.id.tvDetailRoom)).setText(
                details.room == null || details.room.trim().isEmpty()
                        ? getString(R.string.value_not_set) : details.room);
        ((TextView) findViewById(R.id.tvDetailLecturer)).setText(
                details.lecturer == null || details.lecturer.trim().isEmpty()
                        ? getString(R.string.value_not_set) : details.lecturer);
        ((TextView) findViewById(R.id.tvDetailAppliedPeriod)).setText(
                formatDate(details.validFromEpochDay) + " – " + formatDate(details.validToEpochDay));
        ((TextView) findViewById(R.id.tvDetailNote)).setText(
                details.note == null || details.note.trim().isEmpty()
                        ? getString(R.string.value_no_note) : details.note);
    }

    private void openEditForm() {
        if (currentSchedule == null) {
            return;
        }
        startActivity(new Intent(this, ScheduleFormActivity.class)
                .putExtra(ScheduleFormActivity.EXTRA_SCHEDULE_ID, currentSchedule.id));
    }

    private void showDeleteConfirmDialog() {
        if (currentSchedule == null || deleting) {
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }
        TextView title = dialogView.findViewById(R.id.tvDialogTitle);
        TextView message = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton cancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton confirm = dialogView.findViewById(R.id.btnDialogConfirm);
        title.setText(R.string.title_delete_schedule);
        message.setText(R.string.msg_confirm_delete_schedule);
        confirm.setText(R.string.action_delete);
        cancel.setOnClickListener(v -> dialog.dismiss());
        confirm.setOnClickListener(v -> {
            dialog.dismiss();
            deleteCurrentSchedule();
        });
        dialog.show();
    }

    private void deleteCurrentSchedule() {
        if (currentSchedule == null || deleting) {
            return;
        }
        deleting = true;
        findViewById(R.id.btnDeleteSchedule).setEnabled(false);
        Future<Integer> deletion = repository.deleteSchedule(currentSchedule);
        new Thread(() -> {
            try {
                int deletedRows = deletion.get();
                runOnUiThread(() -> {
                    if (deletedRows > 0) {
                        NotificationScheduler.syncAll(getApplicationContext());
                        Toast.makeText(this, R.string.msg_delete_schedule_success, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        showDeleteError();
                    }
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(this::showDeleteError);
            } catch (ExecutionException exception) {
                runOnUiThread(this::showDeleteError);
            }
        }, "schedule-delete-result").start();
    }

    private void showDeleteError() {
        deleting = false;
        findViewById(R.id.btnDeleteSchedule).setEnabled(true);
        Toast.makeText(this, R.string.error_schedule_save, Toast.LENGTH_LONG).show();
    }

    private void showScheduleNotFound() {
        Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private static String formatTime(int minutes) {
        int hourOfDay = minutes / 60;
        int hour12 = hourOfDay % 12 == 0 ? 12 : hourOfDay % 12;
        return String.format(Locale.US, "%02d:%02d %s", hour12, minutes % 60,
                hourOfDay >= 12 ? "PM" : "AM");
    }

    private static String formatDate(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
