package com.example.demo.ui.occurrence;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.demo.R;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;
import com.example.demo.ui.detail.ScheduleDetailActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import com.example.demo.notifications.NotificationScheduler;

/**
 * Shows one concrete class session and lets the user cancel, change or restore only that
 * session. With EXTRA_NEW_MAKEUP it instead opens the form for a brand-new makeup session.
 */
public class OccurrenceActivity extends AppCompatActivity {
    public static final String EXTRA_SCHEDULE_ID = "schedule_id";
    public static final String EXTRA_EXCEPTION_ID = "exception_id";
    public static final String EXTRA_EPOCH_DAY = "epoch_day";
    public static final String EXTRA_NEW_MAKEUP = "new_makeup";
    public static final String EXTRA_CLASS_SECTION_ID = "class_section_id";
    public static final String EXTRA_START_MINUTE = "start_minute";
    public static final String EXTRA_END_MINUTE = "end_minute";
    public static final String EXTRA_ROOM = "room";

    private static final int FORM_NONE = 0;
    private static final int FORM_CANCEL = 1;
    private static final int FORM_MODIFY = 2;
    private static final int FORM_MAKEUP = 3;
    private static final int FORM_MAKEUP_NEW = 4;

    private AppRepository repository;
    private ScheduleSnapshot snapshot;
    private ClassOccurrence occurrence;
    private ClassSectionDetails selectedSection;
    private boolean newMakeup;
    private boolean busy;

    private int formMode = FORM_NONE;
    private LocalDate formDate;
    private int formStart;
    private int formEnd;

    private MaterialToolbar toolbar;
    private View cardInfo;
    private View layoutActions;
    private TextView tvSubject;
    private TextView tvClass;
    private TextView tvDate;
    private TextView tvTime;
    private TextView tvRoom;
    private TextView tvLecturer;
    private TextView tvStatus;
    private TextView tvOriginal;
    private TextView tvReason;
    private MaterialButton btnCancel;
    private MaterialButton btnModify;
    private MaterialButton btnRestore;
    private MaterialButton btnNewMakeup;
    private MaterialButton btnDeleteMakeup;
    private MaterialButton btnOpenWeekly;
    private View cardForm;
    private TextView tvFormTitle;
    private View tilClass;
    private AutoCompleteTextView actvClass;
    private View groupDateTime;
    private View tilRoom;
    private MaterialButton btnPickDate;
    private MaterialButton btnPickStart;
    private MaterialButton btnPickEnd;
    private TextInputEditText etRoom;
    private TextInputEditText etNote;
    private TextView tvOverlap;
    private TextView tvFormError;
    private MaterialButton btnFormSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_occurrence);
        repository = AppRepository.getInstance(this);
        newMakeup = getIntent().getBooleanExtra(EXTRA_NEW_MAKEUP, false);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cardInfo = findViewById(R.id.cardOccInfo);
        layoutActions = findViewById(R.id.layoutOccActions);
        tvSubject = findViewById(R.id.tvOccSubject);
        tvClass = findViewById(R.id.tvOccClass);
        tvDate = findViewById(R.id.tvOccDate);
        tvTime = findViewById(R.id.tvOccTime);
        tvRoom = findViewById(R.id.tvOccRoom);
        tvLecturer = findViewById(R.id.tvOccLecturer);
        tvStatus = findViewById(R.id.tvOccStatus);
        tvOriginal = findViewById(R.id.tvOccOriginal);
        tvReason = findViewById(R.id.tvOccReason);
        btnCancel = findViewById(R.id.btnOccCancel);
        btnModify = findViewById(R.id.btnOccModify);
        btnRestore = findViewById(R.id.btnOccRestore);
        btnNewMakeup = findViewById(R.id.btnOccNewMakeup);
        btnDeleteMakeup = findViewById(R.id.btnOccDeleteMakeup);
        btnOpenWeekly = findViewById(R.id.btnOccOpenWeekly);
        cardForm = findViewById(R.id.cardOccForm);
        tvFormTitle = findViewById(R.id.tvOccFormTitle);
        tilClass = findViewById(R.id.tilOccClass);
        actvClass = findViewById(R.id.actvOccClass);
        groupDateTime = findViewById(R.id.groupOccDateTime);
        tilRoom = findViewById(R.id.tilOccRoom);
        btnPickDate = findViewById(R.id.btnOccPickDate);
        btnPickStart = findViewById(R.id.btnOccPickStart);
        btnPickEnd = findViewById(R.id.btnOccPickEnd);
        etRoom = findViewById(R.id.etOccRoom);
        etNote = findViewById(R.id.etOccNote);
        tvOverlap = findViewById(R.id.tvOccOverlap);
        tvFormError = findViewById(R.id.tvOccFormError);
        btnFormSave = findViewById(R.id.btnOccFormSave);

        btnCancel.setOnClickListener(v -> openForm(FORM_CANCEL));
        btnModify.setOnClickListener(v -> openForm(
                occurrence != null && occurrence.scheduleId == 0 ? FORM_MAKEUP : FORM_MODIFY));
        btnRestore.setOnClickListener(v -> confirmDelete(
                R.string.occ_confirm_restore_title, R.string.occ_confirm_restore_message));
        btnDeleteMakeup.setOnClickListener(v -> confirmDelete(
                R.string.occ_confirm_delete_makeup_title, R.string.occ_confirm_delete_makeup_message));
        btnNewMakeup.setOnClickListener(v -> startMakeupFromCancelled());
        btnOpenWeekly.setOnClickListener(v -> {
            if (occurrence != null && occurrence.scheduleId > 0) {
                Intent intent = new Intent(this, ScheduleDetailActivity.class);
                intent.putExtra(ScheduleDetailActivity.EXTRA_SCHEDULE_ID, occurrence.scheduleId);
                startActivity(intent);
            }
        });
        btnPickDate.setOnClickListener(v -> pickDate());
        btnPickStart.setOnClickListener(v -> pickTime(true));
        btnPickEnd.setOnClickListener(v -> pickTime(false));
        findViewById(R.id.btnOccFormClose).setOnClickListener(v -> closeForm());
        btnFormSave.setOnClickListener(v -> onSave());

        loadSnapshot();
    }

    private void loadSnapshot() {
        Future<ScheduleSnapshot> load = repository.getScheduleSnapshot();
        new Thread(() -> {
            try {
                ScheduleSnapshot result = load.get();
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    onLoaded(result == null ? new ScheduleSnapshot(null, null, null) : result);
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | RuntimeException exception) {
                runOnUiThread(this::failAndFinish);
            }
        }, "occurrence-load").start();
    }

    private void failAndFinish() {
        Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void onLoaded(ScheduleSnapshot loaded) {
        snapshot = loaded;
        if (newMakeup) {
            setupNewMakeup();
            return;
        }
        long scheduleId = getIntent().getLongExtra(EXTRA_SCHEDULE_ID, 0);
        long exceptionId = getIntent().getLongExtra(EXTRA_EXCEPTION_ID, 0);
        long epochDay = getIntent().getLongExtra(EXTRA_EPOCH_DAY, Long.MIN_VALUE);
        occurrence = null;
        if (epochDay != Long.MIN_VALUE) {
            List<ClassOccurrence> day = ScheduleResolver.resolveDay(snapshot, epochDay, true);
            for (ClassOccurrence item : day) {
                boolean match = scheduleId > 0
                        ? item.scheduleId == scheduleId
                        : item.scheduleId == 0 && item.exceptionId == exceptionId;
                if (match) {
                    occurrence = item;
                    break;
                }
            }
        }
        if (occurrence == null) {
            failAndFinish();
            return;
        }
        bindOccurrence();
    }

    // ---- new makeup session -----------------------------------------------------------

    private void setupNewMakeup() {
        List<ClassSectionDetails> sections = snapshot.classSections;
        if (sections.isEmpty()) {
            Toast.makeText(this, R.string.occ_no_class_sections, Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        toolbar.setTitle(R.string.occ_title_new_makeup);
        cardInfo.setVisibility(View.GONE);
        layoutActions.setVisibility(View.GONE);

        List<String> labels = new ArrayList<>();
        long preselected = getIntent().getLongExtra(EXTRA_CLASS_SECTION_ID, 0);
        int selectedIndex = 0;
        for (int i = 0; i < sections.size(); i++) {
            ClassSectionDetails section = sections.get(i);
            labels.add(isBlank(section.classCode)
                    ? safe(section.subjectName)
                    : getString(R.string.occ_class_option, safe(section.subjectName), section.classCode));
            if (section.classSectionId == preselected) {
                selectedIndex = i;
            }
        }
        actvClass.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, labels));
        selectedSection = sections.get(selectedIndex);
        actvClass.setText(labels.get(selectedIndex), false);
        actvClass.setOnItemClickListener((parent, view, position, id) ->
                selectedSection = sections.get(position));

        formMode = FORM_MAKEUP_NEW;
        formDate = LocalDate.ofEpochDay(
                getIntent().getLongExtra(EXTRA_EPOCH_DAY, LocalDate.now().toEpochDay()));
        formStart = getIntent().getIntExtra(EXTRA_START_MINUTE, 7 * 60);
        formEnd = getIntent().getIntExtra(EXTRA_END_MINUTE, 9 * 60);
        String room = getIntent().getStringExtra(EXTRA_ROOM);
        etRoom.setText(safe(room));
        etNote.setText("");
        tvFormTitle.setText(R.string.occ_form_title_new_makeup);
        tilClass.setVisibility(View.VISIBLE);
        groupDateTime.setVisibility(View.VISIBLE);
        tilRoom.setVisibility(View.VISIBLE);
        tvFormError.setVisibility(View.GONE);
        refreshPickerLabels();
        cardForm.setVisibility(View.VISIBLE);
        updateOverlapWarning();
    }

    private void startMakeupFromCancelled() {
        if (occurrence == null) {
            return;
        }
        Intent intent = new Intent(this, OccurrenceActivity.class);
        intent.putExtra(EXTRA_NEW_MAKEUP, true);
        intent.putExtra(EXTRA_CLASS_SECTION_ID, occurrence.classSectionId);
        intent.putExtra(EXTRA_EPOCH_DAY, occurrence.epochDay);
        intent.putExtra(EXTRA_START_MINUTE, occurrence.startMinute);
        intent.putExtra(EXTRA_END_MINUTE, occurrence.endMinute);
        intent.putExtra(EXTRA_ROOM, safe(occurrence.room));
        startActivity(intent);
        finish();
    }

    // ---- existing session ---------------------------------------------------------------

    private void bindOccurrence() {
        ClassOccurrence o = occurrence;
        tvSubject.setText(safe(o.subjectName));
        setOptional(tvClass, isBlank(o.classCode) ? null : getString(R.string.occ_class_line, o.classCode));
        LocalDate date = LocalDate.ofEpochDay(o.epochDay);
        tvDate.setText(date.format(DateTimeFormatter.ofPattern("EEEE, dd/MM/yyyy", new Locale("vi", "VN"))));
        tvTime.setText(getString(R.string.occ_time_range, formatClock(o.startMinute), formatClock(o.endMinute)));
        tvRoom.setText(getString(R.string.occ_room_line,
                isBlank(o.room) ? getString(R.string.home_room_not_set) : o.room));
        tvLecturer.setText(getString(R.string.occ_lecturer_line,
                isBlank(o.lecturer) ? getString(R.string.home_lecturer_not_set) : o.lecturer));

        int labelRes = 0;
        int colorRes = 0;
        if (o.status == ClassOccurrence.STATUS_MODIFIED) {
            labelRes = R.string.schedule_status_modified;
            colorRes = R.color.tag_amber_text;
        } else if (o.status == ClassOccurrence.STATUS_MAKEUP) {
            labelRes = R.string.schedule_status_makeup;
            colorRes = R.color.tag_green_text;
        } else if (o.status == ClassOccurrence.STATUS_CANCELLED) {
            labelRes = R.string.schedule_status_cancelled;
            colorRes = R.color.tag_red_text;
        }
        if (labelRes == 0) {
            tvStatus.setVisibility(View.GONE);
        } else {
            tvStatus.setText(labelRes);
            tvStatus.setTextColor(ContextCompat.getColor(this, colorRes));
            tvStatus.setVisibility(View.VISIBLE);
        }
        if (o.originalEpochDay != null && o.originalEpochDay != o.epochDay) {
            tvOriginal.setText(getString(R.string.occ_original_date, LocalDate.ofEpochDay(o.originalEpochDay)
                    .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))));
            tvOriginal.setVisibility(View.VISIBLE);
        } else {
            tvOriginal.setVisibility(View.GONE);
        }
        setOptional(tvReason, isBlank(o.changeNote) ? null : getString(R.string.occ_reason, o.changeNote.trim()));

        boolean regular = o.status == ClassOccurrence.STATUS_REGULAR;
        boolean modified = o.status == ClassOccurrence.STATUS_MODIFIED;
        boolean cancelled = o.status == ClassOccurrence.STATUS_CANCELLED;
        boolean makeup = o.status == ClassOccurrence.STATUS_MAKEUP;

        btnCancel.setVisibility(regular || modified ? View.VISIBLE : View.GONE);
        btnModify.setText(makeup ? R.string.occ_action_edit_makeup
                : modified ? R.string.occ_action_edit_change : R.string.occ_action_modify);
        btnModify.setVisibility(View.VISIBLE);
        btnRestore.setText(cancelled ? R.string.occ_action_restore_cancelled : R.string.occ_action_restore);
        btnRestore.setVisibility(modified || cancelled ? View.VISIBLE : View.GONE);
        btnNewMakeup.setVisibility(cancelled ? View.VISIBLE : View.GONE);
        btnDeleteMakeup.setVisibility(makeup ? View.VISIBLE : View.GONE);
        btnOpenWeekly.setVisibility(o.scheduleId > 0 ? View.VISIBLE : View.GONE);
    }

    // ---- form -------------------------------------------------------------------------

    private void openForm(int mode) {
        if (occurrence == null || busy) {
            return;
        }
        formMode = mode;
        formDate = LocalDate.ofEpochDay(occurrence.epochDay);
        formStart = occurrence.startMinute;
        formEnd = occurrence.endMinute;
        etRoom.setText(safe(occurrence.room));
        boolean keepNote = occurrence.status != ClassOccurrence.STATUS_REGULAR;
        etNote.setText(keepNote ? safe(occurrence.changeNote) : "");
        tvFormError.setVisibility(View.GONE);

        int titleRes = mode == FORM_CANCEL ? R.string.occ_form_title_cancel
                : mode == FORM_MAKEUP ? R.string.occ_form_title_makeup : R.string.occ_form_title_modify;
        tvFormTitle.setText(titleRes);
        tilClass.setVisibility(View.GONE);
        int detailVisibility = mode == FORM_CANCEL ? View.GONE : View.VISIBLE;
        groupDateTime.setVisibility(detailVisibility);
        tilRoom.setVisibility(detailVisibility);
        refreshPickerLabels();
        cardForm.setVisibility(View.VISIBLE);
        cardForm.requestFocus();
        updateOverlapWarning();
    }

    private void closeForm() {
        if (newMakeup) {
            finish();
            return;
        }
        formMode = FORM_NONE;
        cardForm.setVisibility(View.GONE);
    }

    private void refreshPickerLabels() {
        btnPickDate.setText(getString(R.string.occ_pick_date,
                formDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))));
        btnPickStart.setText(getString(R.string.occ_pick_start, formatClock(formStart)));
        btnPickEnd.setText(getString(R.string.occ_pick_end, formatClock(formEnd)));
    }

    private void pickDate() {
        new DatePickerDialog(this, (picker, year, month, day) -> {
            formDate = LocalDate.of(year, month + 1, day);
            refreshPickerLabels();
            updateOverlapWarning();
        }, formDate.getYear(), formDate.getMonthValue() - 1, formDate.getDayOfMonth()).show();
    }

    private void pickTime(boolean start) {
        int current = start ? formStart : formEnd;
        new TimePickerDialog(this, (picker, hour, minute) -> {
            if (start) {
                formStart = hour * 60 + minute;
            } else {
                formEnd = hour * 60 + minute;
            }
            refreshPickerLabels();
            updateOverlapWarning();
        }, current / 60, current % 60, true).show();
    }

    private String overlapText() {
        if (snapshot == null || formMode == FORM_NONE || formMode == FORM_CANCEL || formEnd <= formStart) {
            return null;
        }
        long ignoreSchedule = occurrence == null ? 0 : occurrence.scheduleId;
        long ignoreException = occurrence == null ? 0 : occurrence.exceptionId;
        List<ClassOccurrence> day = ScheduleResolver.resolveDay(snapshot, formDate.toEpochDay(), false);
        List<ClassOccurrence> overlaps = ScheduleResolver.findOverlaps(
                day, formStart, formEnd, ignoreSchedule, ignoreException);
        if (overlaps.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (ClassOccurrence item : overlaps) {
            if (text.length() > 0) {
                text.append('\n');
            }
            text.append("• ").append(safe(item.subjectName)).append(' ')
                    .append(formatClock(item.startMinute)).append('–').append(formatClock(item.endMinute));
        }
        return text.toString();
    }

    private void updateOverlapWarning() {
        String overlaps = overlapText();
        if (overlaps == null) {
            tvOverlap.setVisibility(View.GONE);
        } else {
            tvOverlap.setText(getString(R.string.occ_overlap_warning, overlaps));
            tvOverlap.setVisibility(View.VISIBLE);
        }
    }

    private void showFormError(String message) {
        tvFormError.setText(message);
        tvFormError.setVisibility(View.VISIBLE);
    }

    // ---- save / delete ----------------------------------------------------------------

    private void onSave() {
        if (busy || formMode == FORM_NONE || (occurrence == null && !newMakeup)) {
            return;
        }
        tvFormError.setVisibility(View.GONE);
        ScheduleExceptionEntity entity;
        try {
            entity = buildException();
        } catch (IllegalArgumentException exception) {
            showFormError(exception.getMessage());
            return;
        }
        String overlaps = overlapText();
        if (overlaps != null) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.occ_overlap_confirm_title)
                    .setMessage(getString(R.string.occ_overlap_confirm_message, overlaps))
                    .setPositiveButton(R.string.occ_overlap_confirm_save, (d, w) -> persist(entity))
                    .setNegativeButton(R.string.occ_confirm_no, null)
                    .show();
        } else {
            persist(entity);
        }
    }

    private ScheduleExceptionEntity buildException() {
        String note = textOf(etNote);
        if (formMode == FORM_MAKEUP_NEW) {
            if (selectedSection == null) {
                throw new IllegalArgumentException(getString(R.string.occ_error_pick_class));
            }
            if (formEnd <= formStart) {
                throw new IllegalArgumentException(getString(R.string.occ_error_time_order));
            }
            return ScheduleExceptionEntity.makeup(selectedSection.classSectionId, formDate.toEpochDay(),
                    formStart, formEnd, textOf(etRoom), note);
        }
        ClassOccurrence o = occurrence;
        if (formMode == FORM_CANCEL) {
            return ScheduleExceptionEntity.cancel(o.scheduleId, o.classSectionId, originalDay(), note);
        }
        if (formEnd <= formStart) {
            throw new IllegalArgumentException(getString(R.string.occ_error_time_order));
        }
        String room = textOf(etRoom);
        if (formMode == FORM_MAKEUP) {
            return ScheduleExceptionEntity.makeup(o.classSectionId, formDate.toEpochDay(),
                    formStart, formEnd, room, note);
        }
        ScheduleDetails weekly = findWeeklySchedule(o.scheduleId);
        if (weekly == null) {
            throw new IllegalArgumentException(getString(R.string.error_schedule_not_found));
        }
        long original = originalDay();
        Long newDay = formDate.toEpochDay() != original ? formDate.toEpochDay() : null;
        boolean timeChanged = formStart != weekly.startMinute || formEnd != weekly.endMinute;
        Integer newStart = timeChanged ? formStart : null;
        Integer newEnd = timeChanged ? formEnd : null;
        String newRoom = !room.isEmpty() && !room.equals(safe(weekly.room).trim()) ? room : null;
        return ScheduleExceptionEntity.modify(o.scheduleId, o.classSectionId, original,
                newDay, newStart, newEnd, newRoom, note);
    }

    private long originalDay() {
        return occurrence.originalEpochDay != null ? occurrence.originalEpochDay : occurrence.epochDay;
    }

    private ScheduleDetails findWeeklySchedule(long scheduleId) {
        for (ScheduleDetails schedule : snapshot.schedules) {
            if (schedule.scheduleId == scheduleId) {
                return schedule;
            }
        }
        return null;
    }

    private void persist(ScheduleExceptionEntity entity) {
        setBusy(true);
        long oldMakeupId = formMode == FORM_MAKEUP ? occurrence.exceptionId : 0;
        Future<Long> save = repository.saveScheduleException(entity);
        new Thread(() -> {
            try {
                save.get();
                if (oldMakeupId > 0) {
                    // Makeup edit: new row is saved first so a failure never loses the old one.
                    repository.deleteScheduleException(oldMakeupId).get();
                }
                runOnUiThread(() -> {
                    NotificationScheduler.syncAll(getApplicationContext());
                    Toast.makeText(this, R.string.occ_saved, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(() -> onPersistFailed(null));
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                runOnUiThread(() -> onPersistFailed(cause == null ? null : cause.getMessage()));
            } catch (RuntimeException exception) {
                runOnUiThread(() -> onPersistFailed(null));
            }
        }, "occurrence-save").start();
    }

    private void onPersistFailed(String message) {
        setBusy(false);
        showFormError(isBlank(message) ? getString(R.string.occ_error_save) : message);
    }

    private void confirmDelete(int titleRes, int messageRes) {
        if (busy || occurrence == null || occurrence.exceptionId <= 0) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setMessage(messageRes)
                .setPositiveButton(R.string.occ_confirm_yes, (d, w) -> deleteException())
                .setNegativeButton(R.string.occ_confirm_no, null)
                .show();
    }

    private void deleteException() {
        setBusy(true);
        Future<Integer> delete = repository.deleteScheduleException(occurrence.exceptionId);
        new Thread(() -> {
            try {
                delete.get();
                runOnUiThread(() -> {
                    NotificationScheduler.syncAll(getApplicationContext());
                    Toast.makeText(this, R.string.occ_restored, Toast.LENGTH_SHORT).show();
                    finish();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                runOnUiThread(this::onDeleteFailed);
            } catch (ExecutionException | RuntimeException exception) {
                runOnUiThread(this::onDeleteFailed);
            }
        }, "occurrence-delete").start();
    }

    private void onDeleteFailed() {
        setBusy(false);
        Toast.makeText(this, R.string.occ_error_save, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean value) {
        busy = value;
        btnFormSave.setEnabled(!value);
        btnCancel.setEnabled(!value);
        btnModify.setEnabled(!value);
        btnRestore.setEnabled(!value);
        btnNewMakeup.setEnabled(!value);
        btnDeleteMakeup.setEnabled(!value);
    }

    // ---- helpers ----------------------------------------------------------------------

    private static void setOptional(TextView view, String text) {
        if (text == null) {
            view.setVisibility(View.GONE);
        } else {
            view.setText(text);
            view.setVisibility(View.VISIBLE);
        }
    }

    private static String textOf(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }

    private static String formatClock(int minuteOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}