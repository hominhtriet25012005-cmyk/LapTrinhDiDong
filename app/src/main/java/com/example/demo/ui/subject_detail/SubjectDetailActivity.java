package com.example.demo.ui.subject_detail;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.example.demo.R;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.relation.SubjectWithClassSections;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.ui.subject_form.SubjectFormActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SubjectDetailActivity extends AppCompatActivity {
    public static final String EXTRA_SUBJECT_ID = "subject_id";

    private AppRepository repository;
    private SubjectEntity currentSubject;
    private LiveData<SemesterEntity> activeSemesterSource;
    private long observedSemesterId;
    private boolean deleting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_detail);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        findViewById(R.id.btnEditSubject).setOnClickListener(v -> openEditForm());
        findViewById(R.id.btnDeleteSubject).setOnClickListener(v -> showDeleteConfirmDialog());
        findViewById(R.id.btnAddClassSection).setOnClickListener(v -> showClassSectionDialog(null));

        long subjectId = getIntent().getLongExtra(EXTRA_SUBJECT_ID, 0L);
        if (subjectId <= 0) {
            showSubjectNotFound();
            return;
        }

        repository = AppRepository.getInstance(getApplicationContext());
        repository.observeSubjectWithClassSections(subjectId).observe(this, this::bindSubject);
    }

    private void bindSubject(SubjectWithClassSections details) {
        if (details == null || details.subject == null) {
            if (deleting) {
                return;
            }
            showSubjectNotFound();
            return;
        }
        currentSubject = details.subject;

        TextView tvInitials = findViewById(R.id.tvSubjectInitials);
        TextView tvCode = findViewById(R.id.tvSubjectCode);
        TextView tvName = findViewById(R.id.tvSubjectName);
        TextView tvCredits = findViewById(R.id.tvSubjectCredits);
        TextView tvLecturer = findViewById(R.id.tvDetailLecturer);
        TextView tvNote = findViewById(R.id.tvDetailNote);

        tvInitials.setText(initials(currentSubject.name));
        tvCode.setText(safe(currentSubject.code));
        tvName.setText(safe(currentSubject.name));
        tvCredits.setText(getString(R.string.subject_credits_format, currentSubject.credits));
        tvLecturer.setText(currentSubject.defaultLecturer == null || currentSubject.defaultLecturer.trim().isEmpty()
                ? getString(R.string.value_not_set)
                : currentSubject.defaultLecturer);
        tvNote.setText(currentSubject.note == null || currentSubject.note.trim().isEmpty()
                ? getString(R.string.value_no_note)
                : currentSubject.note);
        applyColor(tvInitials, currentSubject.colorKey);
        bindRelatedClasses(details.classSections);
        observeSemester(currentSubject.semesterId);
    }

    private void observeSemester(long semesterId) {
        if (observedSemesterId == semesterId && activeSemesterSource != null) {
            return;
        }
        if (activeSemesterSource != null) {
            activeSemesterSource.removeObservers(this);
        }
        observedSemesterId = semesterId;
        activeSemesterSource = repository.observeSemester(semesterId);
        activeSemesterSource.observe(this, semester -> {
            TextView tvSemester = findViewById(R.id.tvDetailSemester);
            if (semester == null) {
                tvSemester.setText(R.string.value_not_set);
            } else if (semester.academicYear == null || semester.academicYear.trim().isEmpty()) {
                tvSemester.setText(safe(semester.name));
            } else {
                tvSemester.setText(getString(R.string.semester_label_format,
                        semester.name, semester.academicYear));
            }
        });
    }

    private void bindRelatedClasses(java.util.List<ClassSectionEntity> classSections) {
        ChipGroup chipGroup = findViewById(R.id.chipClassSections);
        TextView emptyState = findViewById(R.id.tvEmptySubjectClasses);
        chipGroup.removeAllViews();
        if (classSections == null || classSections.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
            return;
        }
        emptyState.setVisibility(View.GONE);
        for (ClassSectionEntity classSection : classSections) {
            Chip chip = new Chip(this);
            chip.setText(classSection.lecturer == null || classSection.lecturer.trim().isEmpty()
                    ? classSection.classCode
                    : getString(R.string.class_section_chip_format,
                            classSection.classCode, classSection.lecturer));
            chip.setCheckable(false);
            chip.setClickable(true);
            chip.setOnClickListener(v -> showClassSectionDialog(classSection));
            chip.setOnLongClickListener(v -> {
                showDeleteClassSectionDialog(classSection);
                return true;
            });
            chipGroup.addView(chip);
        }
    }

    private void showClassSectionDialog(ClassSectionEntity existing) {
        if (currentSubject == null) {
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);

        EditText codeField = new EditText(this);
        codeField.setSingleLine(true);
        codeField.setHint(R.string.field_class_code);
        if (existing != null) {
            codeField.setText(existing.classCode);
        }
        content.addView(codeField);

        EditText lecturerField = new EditText(this);
        lecturerField.setSingleLine(true);
        lecturerField.setHint(R.string.field_class_lecturer);
        if (existing != null) {
            lecturerField.setText(existing.lecturer);
        }
        content.addView(lecturerField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.title_add_class_section : R.string.title_edit_class_section)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, (ignored, which) -> ignored.dismiss())
                .setPositiveButton(R.string.action_save_class_section, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = codeField.getText() == null ? "" : codeField.getText().toString().trim();
            if (code.isEmpty()) {
                codeField.setError(getString(R.string.error_class_code_required));
                return;
            }
            ClassSectionEntity classSection = new ClassSectionEntity(
                    currentSubject.id,
                    code,
                    lecturerField.getText() == null ? "" : lecturerField.getText().toString().trim(),
                    existing == null ? "" : safe(existing.note)
            );
            if (existing != null) {
                classSection.id = existing.id;
            }
            saveClassSection(classSection, codeField, dialog);
        }));
        dialog.show();
    }

    private void saveClassSection(ClassSectionEntity classSection, EditText codeField, AlertDialog dialog) {
        java.util.concurrent.Future<?> write = classSection.id > 0
                ? repository.updateClassSection(classSection)
                : repository.insertClassSection(classSection);
        new Thread(() -> {
            try {
                write.get();
                runOnUiThread(() -> {
                    dialog.dismiss();
                    Toast.makeText(this, R.string.msg_save_class_section_success, Toast.LENGTH_SHORT).show();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> showClassSectionSaveError(codeField, exception));
            } catch (java.util.concurrent.ExecutionException exception) {
                runOnUiThread(() -> showClassSectionSaveError(codeField, exception.getCause()));
            }
        }, "class-section-write-result").start();
    }

    private void showClassSectionSaveError(EditText codeField, Throwable error) {
        if (error != null && error.getMessage() != null
                && error.getMessage().contains("Mã lớp học phần đã tồn tại")) {
            codeField.setError(getString(R.string.error_class_code_duplicate));
        } else {
            Toast.makeText(this, R.string.error_class_section_save, Toast.LENGTH_LONG).show();
        }
    }

    private void showDeleteClassSectionDialog(ClassSectionEntity classSection) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.title_delete_class_section)
                .setMessage(R.string.msg_confirm_delete_class_section)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> {
                    java.util.concurrent.Future<Integer> delete = repository.deleteClassSection(classSection);
                    new Thread(() -> {
                        try {
                            int deletedRows = delete.get();
                            runOnUiThread(() -> Toast.makeText(this,
                                    deletedRows > 0 ? R.string.msg_delete_class_section_success
                                            : R.string.error_class_section_save,
                                    Toast.LENGTH_SHORT).show());
                        } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            runOnUiThread(() -> Toast.makeText(this, R.string.error_class_section_save,
                                    Toast.LENGTH_LONG).show());
                        } catch (java.util.concurrent.ExecutionException exception) {
                            runOnUiThread(() -> Toast.makeText(this, R.string.error_class_section_save,
                                    Toast.LENGTH_LONG).show());
                        }
                    }, "class-section-delete-result").start();
                })
                .show();
    }

    private void openEditForm() {
        if (currentSubject == null) {
            return;
        }
        startActivity(new android.content.Intent(this, SubjectFormActivity.class)
                .putExtra(SubjectFormActivity.EXTRA_SUBJECT_ID, currentSubject.id));
    }

    private void showDeleteConfirmDialog() {
        if (currentSubject == null) {
            return;
        }
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_confirm, null);
        AlertDialog dialog = new AlertDialog.Builder(this).setView(dialogView).create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvMsg = dialogView.findViewById(R.id.tvDialogMessage);
        MaterialButton btnCancel = dialogView.findViewById(R.id.btnDialogCancel);
        MaterialButton btnConfirm = dialogView.findViewById(R.id.btnDialogConfirm);
        tvTitle.setText(R.string.title_delete_subject);
        tvMsg.setText(R.string.msg_confirm_delete_subject);
        btnConfirm.setText(R.string.action_delete);

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnConfirm.setOnClickListener(v -> {
            dialog.dismiss();
            deleteCurrentSubject();
        });
        dialog.show();
    }

    private void deleteCurrentSubject() {
        SubjectEntity subject = currentSubject;
        if (subject == null || deleting) {
            return;
        }
        deleting = true;
        findViewById(R.id.btnDeleteSubject).setEnabled(false);
        Future<Integer> delete = repository.deleteSubject(subject);
        new Thread(() -> {
            try {
                int rows = delete.get();
                runOnUiThread(() -> {
                    if (rows > 0) {
                        Toast.makeText(this, R.string.msg_delete_subject_success, Toast.LENGTH_SHORT).show();
                        finish();
                    } else {
                        deleting = false;
                        findViewById(R.id.btnDeleteSubject).setEnabled(true);
                        Toast.makeText(this, R.string.error_subject_save, Toast.LENGTH_LONG).show();
                    }
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> showDeleteError());
            } catch (ExecutionException exception) {
                runOnUiThread(() -> showDeleteError());
            }
        }, "subject-delete-result").start();
    }

    private void showDeleteError() {
        deleting = false;
        findViewById(R.id.btnDeleteSubject).setEnabled(true);
        Toast.makeText(this, R.string.error_subject_save, Toast.LENGTH_LONG).show();
    }

    private void showSubjectNotFound() {
        Toast.makeText(this, R.string.error_subject_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void applyColor(TextView view, String colorKey) {
        if ("green".equalsIgnoreCase(colorKey)) {
            view.setBackgroundResource(R.drawable.bg_round_icon_green);
        } else if ("amber".equalsIgnoreCase(colorKey) || "orange".equalsIgnoreCase(colorKey)) {
            view.setBackgroundResource(R.drawable.bg_round_icon_amber);
        } else if ("violet".equalsIgnoreCase(colorKey) || "purple".equalsIgnoreCase(colorKey)) {
            view.setBackgroundResource(R.drawable.bg_round_icon_violet);
        } else {
            view.setBackgroundResource(R.drawable.bg_round_icon_blue);
        }
    }

    private static String initials(String name) {
        String normalized = safe(name).trim();
        if (normalized.isEmpty()) {
            return "?";
        }
        String[] words = normalized.split("\\s+");
        if (words.length > 1) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }
        return normalized.substring(0, Math.min(2, normalized.length())).toUpperCase(Locale.getDefault());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
