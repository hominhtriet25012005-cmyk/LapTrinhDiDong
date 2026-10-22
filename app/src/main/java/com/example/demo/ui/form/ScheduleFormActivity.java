package com.example.demo.ui.form;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;

import com.example.demo.R;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.validation.ScheduleConflictException;
import com.example.demo.data.validation.ScheduleFormMapper;
import com.example.demo.notifications.NotificationPreferences;
import com.example.demo.notifications.NotificationScheduler;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ScheduleFormActivity extends AppCompatActivity {
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";

    private final List<SubjectEntity> subjects = new ArrayList<>();
    private final List<ClassSectionEntity> classSections = new ArrayList<>();
    private AutoCompleteTextView actvSubject;
    private AutoCompleteTextView actvClassSection;
    private AutoCompleteTextView actvDayOfWeek;
    private TextInputLayout tilSubject;
    private TextInputLayout tilClassSection;
    private TextInputLayout tilStartTime;
    private TextInputLayout tilEndTime;
    private TextInputLayout tilFromDate;
    private TextInputLayout tilToDate;
    private TextInputEditText etStartTime;
    private TextInputEditText etEndTime;
    private TextInputEditText etRoom;
    private TextInputEditText etLecturer;
    private TextInputEditText etFromDate;
    private TextInputEditText etToDate;
    private TextInputEditText etNotes;
    private MaterialButton btnSaveSchedule;
    private AppRepository repository;
    private SubjectEntity selectedSubject;
    private ClassSectionEntity selectedClassSection;
    private ScheduleDetails editingDetails;
    private LiveData<List<ClassSectionEntity>> activeClassSectionsSource;
    private LiveData<SemesterEntity> activeSemesterSource;
    private long scheduleId;
    private long pendingClassSectionId;
    private long selectedClassSectionId;
    private int selectedDayOfWeek;
    private boolean saving;
    private String autoFromDate;
    private String autoToDate;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_schedule_form);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        actvSubject = findViewById(R.id.actvSubject);
        actvClassSection = findViewById(R.id.actvClassSection);
        actvDayOfWeek = findViewById(R.id.actvDayOfWeek);
        tilSubject = findViewById(R.id.tilSubject);
        tilClassSection = findViewById(R.id.tilClassSection);
        tilStartTime = findViewById(R.id.tilStartTime);
        tilEndTime = findViewById(R.id.tilEndTime);
        tilFromDate = findViewById(R.id.tilFromDate);
        tilToDate = findViewById(R.id.tilToDate);
        etStartTime = findViewById(R.id.etStartTime);
        etEndTime = findViewById(R.id.etEndTime);
        etRoom = findViewById(R.id.etRoom);
        etLecturer = findViewById(R.id.etLecturer);
        etFromDate = findViewById(R.id.etFromDate);
        etToDate = findViewById(R.id.etToDate);
        etNotes = findViewById(R.id.etNotes);
        btnSaveSchedule = findViewById(R.id.btnSaveSchedule);

        repository = AppRepository.getInstance(getApplicationContext());
        scheduleId = getIntent().getLongExtra(EXTRA_SCHEDULE_ID, 0L);
        setupDayDropdown();
        setupPickers();
        tilStartTime.setEndIconOnClickListener(v -> showTimePicker(etStartTime));
        tilEndTime.setEndIconOnClickListener(v -> showTimePicker(etEndTime));
        tilFromDate.setEndIconOnClickListener(v -> showDatePicker(etFromDate));
        tilToDate.setEndIconOnClickListener(v -> showDatePicker(etToDate));
        findViewById(R.id.btnAddClassSection).setOnClickListener(v -> showAddClassSectionDialog());
        btnSaveSchedule.setOnClickListener(v -> saveSchedule());

        if (scheduleId > 0) {
            toolbar.setTitle(R.string.title_edit_schedule);
            repository.observeScheduleDetails(scheduleId).observe(this, details -> {
                if (details == null) {
                    Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editingDetails = details;
                populateScheduleDetails(details);
                selectSubjectById(details.subjectId, details.classSectionId);
            });
        }

        repository.observeAllSubjects().observe(this, this::populateSubjects);
    }

    private void setupDayDropdown() {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        actvDayOfWeek.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, days));
        actvDayOfWeek.setOnItemClickListener((parent, view, position, id) ->
                selectedDayOfWeek = position + 1);
        Calendar today = Calendar.getInstance();
        selectedDayOfWeek = (today.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1;
        actvDayOfWeek.setText(days[selectedDayOfWeek - 1], false);
    }

    private void setupPickers() {
        etStartTime.setOnClickListener(v -> showTimePicker(etStartTime));
        etEndTime.setOnClickListener(v -> showTimePicker(etEndTime));
        etFromDate.setOnClickListener(v -> showDatePicker(etFromDate));
        etToDate.setOnClickListener(v -> showDatePicker(etToDate));
    }

    private void populateSubjects(List<SubjectEntity> values) {
        subjects.clear();
        if (values != null) {
            subjects.addAll(values);
        }
        List<String> labels = new ArrayList<>();
        for (SubjectEntity subject : subjects) {
            labels.add(getString(R.string.schedule_subject_class_format,
                    safe(subject.code), safe(subject.name)));
        }
        actvSubject.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labels));
        actvSubject.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < subjects.size()) {
                selectSubject(subjects.get(position), 0L);
            }
        });

        if (subjects.isEmpty()) {
            tilSubject.setError(getString(R.string.error_schedule_no_subjects));
            btnSaveSchedule.setEnabled(false);
            return;
        }
        tilSubject.setError(null);
        btnSaveSchedule.setEnabled(true);
        if (selectedSubject == null && scheduleId == 0) {
            selectSubject(subjects.get(0), 0L);
        } else if (editingDetails != null) {
            selectSubjectById(editingDetails.subjectId, editingDetails.classSectionId);
        }
    }

    private void selectSubjectById(long subjectId, long classSectionId) {
        for (SubjectEntity subject : subjects) {
            if (subject.id == subjectId) {
                selectSubject(subject, classSectionId);
                return;
            }
        }
    }

    private void selectSubject(SubjectEntity subject, long classSectionId) {
        selectedSubject = subject;
        actvSubject.setText(getString(R.string.schedule_subject_class_format,
                safe(subject.code), safe(subject.name)), false);
        tilSubject.setError(null);
        selectedClassSection = null;
        selectedClassSectionId = 0;
        pendingClassSectionId = classSectionId;
        actvClassSection.setText("", false);
        if (activeClassSectionsSource != null) {
            activeClassSectionsSource.removeObservers(this);
        }
        activeClassSectionsSource = repository.observeClassSections(subject.id);
        activeClassSectionsSource.observe(this, this::populateClassSections);
        observeSubjectSemester(subject.semesterId);
    }

    private void populateClassSections(List<ClassSectionEntity> values) {
        classSections.clear();
        if (values != null) {
            classSections.addAll(values);
        }
        List<String> labels = new ArrayList<>();
        for (ClassSectionEntity classSection : classSections) {
            labels.add(classSection.lecturer == null || classSection.lecturer.trim().isEmpty()
                    ? safe(classSection.classCode)
                    : getString(R.string.class_section_chip_format,
                            safe(classSection.classCode), classSection.lecturer));
        }
        actvClassSection.setAdapter(new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labels));
        actvClassSection.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < classSections.size()) {
                selectClassSection(classSections.get(position), true);
            }
        });

        if (classSections.isEmpty()) {
            selectedClassSection = null;
            selectedClassSectionId = 0;
            tilClassSection.setError(getString(R.string.error_schedule_no_classes));
            return;
        }
        tilClassSection.setError(null);
        ClassSectionEntity choice = findClassSection(pendingClassSectionId);
        if (choice == null && scheduleId == 0 && classSections.size() == 1) {
            choice = classSections.get(0);
        }
        if (choice != null) {
            selectClassSection(choice, false);
        } else {
            selectedClassSection = null;
            selectedClassSectionId = 0;
        }
    }

    private ClassSectionEntity findClassSection(long id) {
        if (id <= 0) {
            return null;
        }
        for (ClassSectionEntity classSection : classSections) {
            if (classSection.id == id) {
                return classSection;
            }
        }
        return null;
    }

    private void selectClassSection(ClassSectionEntity classSection, boolean updateLecturer) {
        selectedClassSection = classSection;
        selectedClassSectionId = classSection.id;
        pendingClassSectionId = classSection.id;
        String label = classSection.lecturer == null || classSection.lecturer.trim().isEmpty()
                ? safe(classSection.classCode)
                : getString(R.string.class_section_chip_format,
                        safe(classSection.classCode), classSection.lecturer);
        actvClassSection.setText(label, false);
        tilClassSection.setError(null);
        if (updateLecturer) {
            etLecturer.setText(classSection.lecturer);
        }
    }

    private void observeSubjectSemester(long semesterId) {
        if (activeSemesterSource != null) {
            activeSemesterSource.removeObservers(this);
        }
        activeSemesterSource = repository.observeSemester(semesterId);
        activeSemesterSource.observe(this, semester -> {
            if (semester == null || scheduleId > 0) {
                return;
            }
            if ((isBlank(etFromDate) || textOf(etFromDate).equals(autoFromDate))
                    && semester.startDateEpochDay != null) {
                autoFromDate = formatDate(semester.startDateEpochDay);
                etFromDate.setText(autoFromDate);
            }
            if ((isBlank(etToDate) || textOf(etToDate).equals(autoToDate))
                    && semester.endDateEpochDay != null) {
                autoToDate = formatDate(semester.endDateEpochDay);
                etToDate.setText(autoToDate);
            }
        });
    }

    private void populateScheduleDetails(ScheduleDetails details) {
        if (details.dayOfWeek < 1 || details.dayOfWeek > 7) {
            Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        selectedDayOfWeek = details.dayOfWeek;
        actvDayOfWeek.setText(getResources().getStringArray(R.array.days_of_week)[details.dayOfWeek - 1], false);
        etStartTime.setText(formatTime(details.startMinute));
        etEndTime.setText(formatTime(details.endMinute));
        etRoom.setText(details.room);
        etLecturer.setText(details.lecturer);
        etFromDate.setText(formatDate(details.validFromEpochDay));
        etToDate.setText(formatDate(details.validToEpochDay));
        etNotes.setText(details.note);
    }

    private void showTimePicker(TextInputEditText target) {
        Calendar now = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(this, (view, hourOfDay, minute) ->
                target.setText(String.format(Locale.US, "%02d:%02d %s",
                        hourOfDay % 12 == 0 ? 12 : hourOfDay % 12,
                        minute,
                        hourOfDay >= 12 ? "PM" : "AM")),
                now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), false);
        dialog.show();
    }

    private void showDatePicker(TextInputEditText target) {
        Calendar initial = Calendar.getInstance();
        try {
            LocalDate date = LocalDate.parse(textOf(target), DateTimeFormatter.ofPattern("dd/MM/uuuu"));
            initial.set(date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth());
        } catch (RuntimeException ignored) {
            // A new schedule defaults the picker to today's date.
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> target.setText(String.format(Locale.ROOT,
                        "%02d/%02d/%04d", dayOfMonth, month + 1, year)),
                initial.get(Calendar.YEAR), initial.get(Calendar.MONTH), initial.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void showAddClassSectionDialog() {
        if (selectedSubject == null) {
            tilSubject.setError(getString(R.string.error_schedule_no_subjects));
            return;
        }
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, padding / 2, padding, 0);
        EditText codeField = new EditText(this);
        codeField.setSingleLine(true);
        codeField.setHint(R.string.field_class_code);
        content.addView(codeField);
        EditText lecturerField = new EditText(this);
        lecturerField.setSingleLine(true);
        lecturerField.setHint(R.string.field_class_lecturer);
        content.addView(lecturerField);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.title_add_class_section)
                .setView(content)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_save_class_section, null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String code = textOf(codeField).trim();
            if (code.isEmpty()) {
                codeField.setError(getString(R.string.error_class_code_required));
                return;
            }
            ClassSectionEntity classSection = new ClassSectionEntity(
                    selectedSubject.id, code, textOf(lecturerField).trim(), "");
            Future<Long> insert = repository.insertClassSection(classSection);
            new Thread(() -> {
                try {
                    long id = insert.get();
                    runOnUiThread(() -> {
                        pendingClassSectionId = id;
                        dialog.dismiss();
                        Toast.makeText(this, R.string.msg_save_class_section_success,
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    runOnUiThread(() -> Toast.makeText(this, R.string.error_class_section_save,
                            Toast.LENGTH_LONG).show());
                } catch (ExecutionException exception) {
                    runOnUiThread(() -> Toast.makeText(this, R.string.error_class_section_save,
                            Toast.LENGTH_LONG).show());
                }
            }, "class-section-create-result").start();
        }));
        dialog.show();
    }

    private void saveSchedule() {
        if (saving) {
            return;
        }
        tilSubject.setError(null);
        tilClassSection.setError(null);
        tilStartTime.setError(null);
        tilEndTime.setError(null);
        tilFromDate.setError(null);
        tilToDate.setError(null);
        if (selectedSubject == null) {
            tilSubject.setError(getString(R.string.error_schedule_no_subjects));
            return;
        }
        if (selectedClassSection == null || selectedClassSectionId <= 0) {
            tilClassSection.setError(getString(R.string.error_schedule_no_classes));
            return;
        }
        try {
            ScheduleEntity schedule = ScheduleFormMapper.toSchedule(
                    scheduleId > 0 ? scheduleId : null,
                    selectedClassSectionId,
                    selectedDayOfWeek,
                    textOf(etStartTime),
                    textOf(etEndTime),
                    textOf(etRoom),
                    textOf(etFromDate),
                    textOf(etToDate),
                    textOf(etNotes)
            );
            if (editingDetails != null) {
                schedule.reminderMinutesBefore = editingDetails.reminderMinutesBefore;
            }
            schedule.notificationEnabled = NotificationPreferences.remindersEnabled(this);
            String lecturer = textOf(etLecturer).trim();
            ClassSectionEntity classSection = new ClassSectionEntity(
                    selectedSubject.id,
                    selectedClassSection.classCode,
                    lecturer.isEmpty() ? safe(selectedClassSection.lecturer) : lecturer,
                    safe(selectedClassSection.note)
            );
            classSection.id = selectedClassSectionId;
            saving = true;
            btnSaveSchedule.setEnabled(false);
            Future<Long> write = repository.saveScheduleWithClassSection(classSection, schedule);
            new Thread(() -> {
                try {
                    long savedScheduleId = write.get();
                    runOnUiThread(() -> {
                        NotificationScheduler.syncAll(getApplicationContext());
                        setResult(RESULT_OK, new Intent().putExtra(EXTRA_SCHEDULE_ID, savedScheduleId));
                        Toast.makeText(this, R.string.msg_save_schedule_success, Toast.LENGTH_SHORT).show();
                        finish();
                    });
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    runOnUiThread(this::showScheduleSaveError);
                } catch (ExecutionException exception) {
                    runOnUiThread(() -> showScheduleSaveError(exception.getCause()));
                }
            }, "schedule-write-result").start();
        } catch (IllegalArgumentException exception) {
            String message = exception.getMessage();
            Toast.makeText(this, message == null ? getString(R.string.error_schedule_required) : message,
                    Toast.LENGTH_LONG).show();
        }
    }

    private void showScheduleSaveError() {
        showScheduleSaveError(null);
    }

    private void showScheduleSaveError(Throwable error) {
        saving = false;
        btnSaveSchedule.setEnabled(!subjects.isEmpty());
        if (error instanceof ScheduleConflictException) {
            Toast.makeText(this, R.string.error_schedule_conflict, Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, R.string.error_schedule_save, Toast.LENGTH_LONG).show();
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

    private static boolean isBlank(TextInputEditText field) {
        return textOf(field).trim().isEmpty();
    }

    private static String textOf(EditText field) {
        return field.getText() == null ? "" : field.getText().toString();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
