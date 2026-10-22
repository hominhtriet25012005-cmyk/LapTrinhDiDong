package com.example.demo.ui.semester;

import android.app.DatePickerDialog;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.demo.R;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.validation.SemesterFormMapper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.materialswitch.MaterialSwitch;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class SemesterFormActivity extends AppCompatActivity {
    public static final String EXTRA_SEMESTER_ID = "semester_id";
    private static final long NO_SEMESTER_ID = -1L;
    private static final int MENU_DELETE_SEMESTER = 1;
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private TextInputLayout tilSemesterName;
    private TextInputLayout tilStartDate;
    private TextInputLayout tilEndDate;
    private TextInputEditText etSemesterName;
    private TextInputEditText etStartDate;
    private TextInputEditText etEndDate;
    private TextInputEditText etSemesterNote;
    private MaterialSwitch switchCurrentSemester;
    private MaterialButton btnCancelSemester;
    private MaterialButton btnSaveSemester;

    private AppRepository repository;
    private MaterialToolbar toolbar;
    private SemesterEntity editSemester;
    private long semesterId = NO_SEMESTER_ID;
    private boolean formReady;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semester_form);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        tilSemesterName = findViewById(R.id.tilSemesterName);
        tilStartDate = findViewById(R.id.tilStartDate);
        tilEndDate = findViewById(R.id.tilEndDate);
        etSemesterName = findViewById(R.id.etSemesterName);
        etStartDate = findViewById(R.id.etStartDate);
        etEndDate = findViewById(R.id.etEndDate);
        etSemesterNote = findViewById(R.id.etSemesterNote);
        switchCurrentSemester = findViewById(R.id.switchCurrentSemester);
        btnCancelSemester = findViewById(R.id.btnCancelSemester);
        btnSaveSemester = findViewById(R.id.btnSaveSemester);

        repository = AppRepository.getInstance(getApplicationContext());
        setupDatePickers();
        btnSaveSemester.setEnabled(false);

        semesterId = getIntent().getLongExtra(EXTRA_SEMESTER_ID, NO_SEMESTER_ID);
        if (semesterId != NO_SEMESTER_ID) {
            toolbar.setTitle(R.string.title_edit_semester);
            addDeleteMenuItem();
            repository.observeSemester(semesterId).observe(this, semester -> {
                if (formReady) {
                    return;
                }
                if (semester == null) {
                    Toast.makeText(this, R.string.error_semester_not_found, Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                editSemester = semester;
                populateData(semester);
                formReady = true;
                btnSaveSemester.setEnabled(true);
            });
        } else {
            repository.observeCurrentSemester().observe(this, currentSemester -> {
                if (formReady) {
                    return;
                }
                switchCurrentSemester.setChecked(currentSemester == null);
                formReady = true;
                btnSaveSemester.setEnabled(true);
            });
        }

        btnCancelSemester.setOnClickListener(v -> finish());
        btnSaveSemester.setOnClickListener(v -> saveSemester());
    }

    private void setupDatePickers() {
        etStartDate.setOnClickListener(v -> showDatePicker(etStartDate));
        etEndDate.setOnClickListener(v -> showDatePicker(etEndDate));
        tilStartDate.setEndIconOnClickListener(v -> showDatePicker(etStartDate));
        tilEndDate.setEndIconOnClickListener(v -> showDatePicker(etEndDate));
    }

    private void showDatePicker(TextInputEditText target) {
        Calendar cal = Calendar.getInstance();
        String currentValue = target.getText() == null ? "" : target.getText().toString().trim();
        if (!currentValue.isEmpty()) {
            try {
                LocalDate selected = LocalDate.parse(currentValue, DISPLAY_DATE_FORMAT);
                cal.set(selected.getYear(), selected.getMonthValue() - 1, selected.getDayOfMonth());
            } catch (RuntimeException ignored) {
                // Keep today's date when the field has no valid selection yet.
            }
        }
        DatePickerDialog dialog = new DatePickerDialog(this,
                (view, year, month, dayOfMonth) -> target.setText(String.format(
                        java.util.Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)),
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
        dialog.show();
    }

    private void populateData(SemesterEntity semester) {
        etSemesterName.setText(semester.name);
        etStartDate.setText(formatDate(semester.startDateEpochDay));
        etEndDate.setText(formatDate(semester.endDateEpochDay));
        etSemesterNote.setText(semester.note);
        switchCurrentSemester.setChecked(semester.isCurrent);
    }

    private void saveSemester() {
        if (!formReady) {
            return;
        }

        clearValidationErrors();
        SemesterEntity semester;
        try {
            semester = SemesterFormMapper.toEntity(
                    editSemester == null ? null : editSemester.id,
                    getText(etSemesterName),
                    getText(etStartDate),
                    getText(etEndDate),
                    getText(etSemesterNote),
                    switchCurrentSemester.isChecked()
            );
        } catch (IllegalArgumentException exception) {
            showValidationError(exception.getMessage());
            return;
        }

        btnSaveSemester.setEnabled(false);
        Future<?> write = editSemester == null
                ? repository.insertSemester(semester)
                : repository.updateSemester(semester);
        CompletableFuture.supplyAsync(() -> await(write))
                .whenComplete((result, error) -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    btnSaveSemester.setEnabled(true);
                    if (error != null) {
                        showWriteError(error);
                    } else if (result instanceof Integer && ((Integer) result) == 0) {
                        Toast.makeText(this, R.string.error_semester_not_found, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.msg_save_semester_success, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }));
    }

    private void addDeleteMenuItem() {
        MenuItem deleteItem = toolbar.getMenu().add(0, MENU_DELETE_SEMESTER, 0, R.string.action_delete);
        deleteItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == MENU_DELETE_SEMESTER) {
                confirmDeleteSemester();
                return true;
            }
            return false;
        });
    }

    private void confirmDeleteSemester() {
        if (editSemester == null) {
            return;
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_delete_semester)
                .setMessage(R.string.msg_confirm_delete_semester)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteSemester())
                .show();
    }

    private void deleteSemester() {
        btnSaveSemester.setEnabled(false);
        CompletableFuture.supplyAsync(() -> await(repository.deleteSemester(editSemester)))
                .whenComplete((deletedRows, error) -> runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) {
                        return;
                    }
                    btnSaveSemester.setEnabled(true);
                    if (error != null) {
                        showWriteError(error);
                    } else if (deletedRows == 0) {
                        Toast.makeText(this, R.string.error_semester_not_found, Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, R.string.msg_delete_semester_success, Toast.LENGTH_SHORT).show();
                        finish();
                    }
                }));
    }

    private void clearValidationErrors() {
        tilSemesterName.setError(null);
        tilStartDate.setError(null);
        tilEndDate.setError(null);
    }

    private void showValidationError(String message) {
        if (message.startsWith("Tên học kỳ")) {
            tilSemesterName.setError(message);
        } else if (message.startsWith("Ngày bắt đầu")) {
            tilStartDate.setError(message);
        } else if (message.startsWith("Ngày kết thúc")) {
            tilEndDate.setError(message);
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }

    private void showWriteError(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        Toast.makeText(this,
                getString(R.string.error_semester_write, message == null ? "" : message),
                Toast.LENGTH_LONG).show();
    }

    private static String getText(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString();
    }

    private static String formatDate(Long epochDay) {
        return epochDay == null ? "" : LocalDate.ofEpochDay(epochDay).format(DISPLAY_DATE_FORMAT);
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new CompletionException(exception);
        } catch (ExecutionException exception) {
            throw new CompletionException(exception.getCause());
        }
    }
}
