package com.example.demo.ui.subject_form;

import android.database.sqlite.SQLiteConstraintException;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.R;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.validation.SubjectFormMapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SubjectFormActivity extends AppCompatActivity {
    public static final String EXTRA_SUBJECT_ID = "subject_id";

    private TextInputLayout tilSubjectName;
    private TextInputLayout tilSubjectCode;
    private TextInputLayout tilSubjectCredits;
    private TextInputEditText etSubjectName;
    private TextInputEditText etSubjectCode;
    private TextInputEditText etSubjectCredits;
    private TextInputEditText etSubjectLecturer;
    private AutoCompleteTextView actvSubjectSemester;
    private TextInputEditText etSubjectNotes;
    private MaterialButton btnSaveSubject;
    private AppRepository repository;
    private final List<SemesterEntity> semesters = new ArrayList<>();
    private SubjectEntity editingSubject;
    private long subjectId;
    private long selectedSemesterId;
    private boolean semesterListLoaded;
    private boolean initialSemesterSelectionApplied;
    private boolean saving;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_subject_form);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tilSubjectName = findViewById(R.id.tilSubjectName);
        tilSubjectCode = findViewById(R.id.tilSubjectCode);
        tilSubjectCredits = findViewById(R.id.tilSubjectCredits);
        etSubjectName = findViewById(R.id.etSubjectName);
        etSubjectCode = findViewById(R.id.etSubjectCode);
        etSubjectCredits = findViewById(R.id.etSubjectCredits);
        etSubjectLecturer = findViewById(R.id.etSubjectLecturer);
        actvSubjectSemester = findViewById(R.id.actvSubjectSemester);
        etSubjectNotes = findViewById(R.id.etSubjectNotes);
        btnSaveSubject = findViewById(R.id.btnSaveSubject);

        findViewById(R.id.btnCancelSubject).setOnClickListener(v -> finish());
        btnSaveSubject.setOnClickListener(v -> saveSubject());

        repository = AppRepository.getInstance(getApplicationContext());
        subjectId = getIntent().getLongExtra(EXTRA_SUBJECT_ID, 0L);
        if (subjectId > 0) {
            toolbar.setTitle(R.string.title_edit_subject);
            repository.observeSubject(subjectId).observe(this, subject -> {
                if (subject == null) {
                    Toast.makeText(this, R.string.error_subject_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editingSubject = subject;
                populateData(subject);
                applyInitialSemesterSelection();
                btnSaveSubject.setEnabled(semesterListLoaded && !semesters.isEmpty());
            });
        }

        repository.observeSemesters().observe(this, values -> {
            semesters.clear();
            if (values != null) {
                semesters.addAll(values);
            }
            semesterListLoaded = true;
            setupSemesterDropdown();
            applyInitialSemesterSelection();
            if (semesters.isEmpty()) {
                actvSubjectSemester.setText("", false);
                actvSubjectSemester.setError(getString(R.string.error_subject_requires_semester));
                btnSaveSubject.setEnabled(false);
            } else {
                actvSubjectSemester.setError(null);
                btnSaveSubject.setEnabled(subjectId == 0 || editingSubject != null);
            }
        });
    }

    private void setupSemesterDropdown() {
        List<String> labels = new ArrayList<>();
        for (SemesterEntity semester : semesters) {
            labels.add(formatSemesterLabel(semester));
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labels);
        actvSubjectSemester.setAdapter(adapter);
        actvSubjectSemester.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < semesters.size()) {
                selectedSemesterId = semesters.get(position).id;
            }
        });
    }

    private void applyInitialSemesterSelection() {
        if (!semesterListLoaded || semesters.isEmpty() || initialSemesterSelectionApplied
                || (subjectId > 0 && editingSubject == null)) {
            return;
        }

        long preferredId = editingSubject == null ? 0 : editingSubject.semesterId;
        if (preferredId == 0) {
            for (SemesterEntity semester : semesters) {
                if (semester.isCurrent) {
                    preferredId = semester.id;
                    break;
                }
            }
        }
        if (preferredId == 0) {
            preferredId = semesters.get(0).id;
        }

        for (SemesterEntity semester : semesters) {
            if (semester.id == preferredId) {
                selectedSemesterId = semester.id;
                actvSubjectSemester.setText(formatSemesterLabel(semester), false);
                initialSemesterSelectionApplied = true;
                return;
            }
        }
        selectedSemesterId = 0;
        actvSubjectSemester.setText("", false);
        initialSemesterSelectionApplied = true;
    }

    private String formatSemesterLabel(SemesterEntity semester) {
        if (semester.academicYear == null || semester.academicYear.trim().isEmpty()) {
            return semester.name == null ? "" : semester.name;
        }
        return getString(R.string.semester_label_format, semester.name, semester.academicYear);
    }

    private void populateData(SubjectEntity subject) {
        etSubjectName.setText(subject.name);
        etSubjectCode.setText(subject.code);
        etSubjectCredits.setText(subject.credits == 0 ? "" : String.valueOf(subject.credits));
        etSubjectLecturer.setText(subject.defaultLecturer);
        etSubjectNotes.setText(subject.note);
    }

    private void saveSubject() {
        if (saving) {
            return;
        }
        tilSubjectName.setError(null);
        tilSubjectCode.setError(null);
        tilSubjectCredits.setError(null);
        actvSubjectSemester.setError(null);

        String name = textOf(etSubjectName);
        String code = textOf(etSubjectCode);
        String credits = textOf(etSubjectCredits);
        if (selectedSemesterId <= 0) {
            actvSubjectSemester.setError(getString(R.string.error_subject_requires_semester));
            return;
        }

        SubjectEntity subject;
        try {
            subject = SubjectFormMapper.toEntity(
                    subjectId > 0 ? subjectId : null,
                    selectedSemesterId,
                    code,
                    name,
                    credits,
                    textOf(etSubjectLecturer),
                    editingSubject == null ? "blue" : editingSubject.colorKey,
                    textOf(etSubjectNotes)
            );
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
            return;
        }

        saving = true;
        btnSaveSubject.setEnabled(false);
        if (subjectId > 0) {
            saveUpdate(repository.updateSubject(subject));
        } else {
            saveInsert(repository.insertSubject(subject));
        }
    }

    private void saveInsert(Future<Long> result) {
        new Thread(() -> {
            try {
                result.get();
                runOnUiThread(() -> finishSave());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(this::showSaveError);
            } catch (ExecutionException exception) {
                runOnUiThread(() -> showSaveError(exception.getCause()));
            }
        }, "subject-insert-result").start();
    }

    private void saveUpdate(Future<Integer> result) {
        new Thread(() -> {
            try {
                int updatedRows = result.get();
                runOnUiThread(() -> {
                    if (updatedRows > 0) {
                        finishSave();
                    } else {
                        showSaveError();
                    }
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(this::showSaveError);
            } catch (ExecutionException exception) {
                runOnUiThread(() -> showSaveError(exception.getCause()));
            }
        }, "subject-update-result").start();
    }

    private void finishSave() {
        Toast.makeText(this, R.string.msg_save_subject_success, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void showSaveError() {
        showSaveError(null);
    }

    private void showSaveError(Throwable error) {
        saving = false;
        btnSaveSubject.setEnabled(!semesters.isEmpty());
        if (containsUniqueConstraint(error)) {
            tilSubjectCode.setError(getString(R.string.error_subject_duplicate_code));
            return;
        }
        Toast.makeText(this, R.string.error_subject_save, Toast.LENGTH_LONG).show();
    }

    private void showValidationError(String message) {
        if (message == null) {
            return;
        }
        if (message.startsWith("Tên")) {
            tilSubjectName.setError(message);
        } else if (message.startsWith("Mã")) {
            tilSubjectCode.setError(message);
        } else if (message.startsWith("Số")) {
            tilSubjectCredits.setError(message);
        } else {
            actvSubjectSemester.setError(message);
        }
    }

    private static boolean containsUniqueConstraint(Throwable error) {
        Throwable current = error;
        while (current != null) {
            if (current instanceof SQLiteConstraintException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null && message.toLowerCase().contains("unique constraint")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static String textOf(TextInputEditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }
}
