package com.example.demo.ui.export;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.example.demo.R;
import com.example.demo.data.export.ExportSessions;
import com.example.demo.data.export.ScheduleExcelExporter;
import com.example.demo.data.export.ScheduleExportCalculator;
import com.example.demo.data.export.SchedulePdfWriter;
import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleSnapshot;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ExportActivity extends AppCompatActivity {
    private static final String STATE_SEMESTER_ID = "export_semester_id";
    private static final String STATE_SCOPE = "export_scope";
    private static final String STATE_FROM = "export_custom_from";
    private static final String STATE_TO = "export_custom_to";
    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int FORMAT_PDF = 0;
    private static final int FORMAT_EXCEL_SCHEDULE = 1;
    private static final int FORMAT_EXCEL_SUBJECTS = 2;
    private static final int SCOPE_CUSTOM = 3;
    private static final String[] SCOPE_LABELS = {
            "Tuần hiện tại", "Tháng hiện tại", "Toàn bộ học kỳ", "Khoảng ngày tùy chọn…"
    };
    private static final ScheduleExportCalculator.Scope[] SCOPES = {
            ScheduleExportCalculator.Scope.CURRENT_WEEK,
            ScheduleExportCalculator.Scope.CURRENT_MONTH,
            ScheduleExportCalculator.Scope.SEMESTER
    };
    private static final DateTimeFormatter PREVIEW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private MaterialCardView cardExportPdf;
    private MaterialCardView cardExportExcelSchedule;
    private MaterialCardView cardExportExcelSubjects;
    private RadioButton rbExportPdf;
    private RadioButton rbExportExcelSchedule;
    private RadioButton rbExportExcelSubjects;
    private AutoCompleteTextView actvExportSemester;
    private AutoCompleteTextView actvExportScope;
    private MaterialButton btnExport;
    private TextView tvScopeDates;
    private TextView tvScopeSummary;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SemesterEntity> semesterOptions = new ArrayList<>();
    private ScheduleSnapshot snapshot = new ScheduleSnapshot(null, null, null);
    private AppRepository repository;
    private ActivityResultLauncher<String> createPdfLauncher;
    private ActivityResultLauncher<String> createExcelScheduleLauncher;
    private ActivityResultLauncher<String> createExcelSubjectsLauncher;
    private SemesterEntity selectedSemester;
    private long restoredSemesterId = -1L;
    private int selectedFormat = FORMAT_PDF;
    private int selectedScopeIndex = 0;
    private long customFrom;
    private long customTo;
    private boolean schedulesLoaded;
    private boolean exporting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        if (savedInstanceState != null) {
            restoredSemesterId = savedInstanceState.getLong(STATE_SEMESTER_ID, -1L);
            selectedScopeIndex = savedInstanceState.getInt(STATE_SCOPE, 0);
            customFrom = savedInstanceState.getLong(STATE_FROM, 0L);
            customTo = savedInstanceState.getLong(STATE_TO, 0L);
        }
        if (selectedScopeIndex < 0 || selectedScopeIndex >= SCOPE_LABELS.length) selectedScopeIndex = 0;
        if (selectedScopeIndex == SCOPE_CUSTOM && (customFrom == 0L || customTo < customFrom)) {
            selectedScopeIndex = 0;
        }

        repository = AppRepository.getInstance(getApplicationContext());
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        cardExportPdf = findViewById(R.id.cardExportPdf);
        cardExportExcelSchedule = findViewById(R.id.cardExportExcelSchedule);
        cardExportExcelSubjects = findViewById(R.id.cardExportExcelSubjects);
        rbExportPdf = findViewById(R.id.rbExportPdf);
        rbExportExcelSchedule = findViewById(R.id.rbExportExcelSchedule);
        rbExportExcelSubjects = findViewById(R.id.rbExportExcelSubjects);
        actvExportSemester = findViewById(R.id.actvExportSemester);
        actvExportScope = findViewById(R.id.actvExportScope);
        tvScopeDates = findViewById(R.id.tvScopeDates);
        tvScopeSummary = findViewById(R.id.tvScopeSummary);
        btnExport = findViewById(R.id.btnExport);

        createPdfLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument("application/pdf"),
                uri -> onFileLocationSelected(uri, FORMAT_PDF));
        createExcelScheduleLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(XLSX_MIME),
                uri -> onFileLocationSelected(uri, FORMAT_EXCEL_SCHEDULE));
        createExcelSubjectsLauncher = registerForActivityResult(
                new ActivityResultContracts.CreateDocument(XLSX_MIME),
                uri -> onFileLocationSelected(uri, FORMAT_EXCEL_SUBJECTS));

        setupDropdowns();
        cardExportPdf.setOnClickListener(v -> selectFormat(FORMAT_PDF));
        cardExportExcelSchedule.setOnClickListener(v -> selectFormat(FORMAT_EXCEL_SCHEDULE));
        cardExportExcelSubjects.setOnClickListener(v -> selectFormat(FORMAT_EXCEL_SUBJECTS));
        btnExport.setOnClickListener(v -> beginExport());

        repository.observeSemesters().observe(this, this::onSemestersChanged);
        loadSnapshot();
        selectFormat(FORMAT_PDF);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_SEMESTER_ID, selectedSemester == null
                ? restoredSemesterId : selectedSemester.id);
        outState.putInt(STATE_SCOPE, selectedScopeIndex);
        outState.putLong(STATE_FROM, customFrom);
        outState.putLong(STATE_TO, customTo);
        super.onSaveInstanceState(outState);
    }

    private void setupDropdowns() {
        ArrayAdapter<String> scopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, SCOPE_LABELS);
        actvExportScope.setAdapter(scopeAdapter);
        actvExportScope.setText(SCOPE_LABELS[selectedScopeIndex], false);
        actvExportScope.setOnItemClickListener((parent, view, position, id) -> {
            if (position == SCOPE_CUSTOM) {
                showCustomRangePicker();
            } else if (position >= 0 && position < SCOPES.length) {
                selectedScopeIndex = position;
                updateScopePreview();
            }
        });
        actvExportSemester.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < semesterOptions.size()) {
                selectedSemester = semesterOptions.get(position);
                restoredSemesterId = selectedSemester.id;
                updateScopePreview();
                updateExportEnabled();
            }
        });
    }

    private void showCustomRangePicker() {
        long today = LocalDate.now().toEpochDay();
        long from = customFrom != 0L ? customFrom : today;
        long to = customTo >= from ? customTo : from;
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.export_custom_range_title)
                .setSelection(new Pair<>(from * 86_400_000L, to * 86_400_000L))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            customFrom = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC)
                    .toLocalDate().toEpochDay();
            customTo = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC)
                    .toLocalDate().toEpochDay();
            selectedScopeIndex = SCOPE_CUSTOM;
            updateScopePreview();
        });
        // Whatever happens, the field shows the scope that is really in effect.
        picker.addOnDismissListener(dialog ->
                actvExportScope.setText(SCOPE_LABELS[selectedScopeIndex], false));
        picker.show(getSupportFragmentManager(), "export_range");
    }

    private void onSemestersChanged(List<SemesterEntity> semesters) {
        semesterOptions.clear();
        if (semesters != null) semesterOptions.addAll(semesters);

        List<String> labels = new ArrayList<>();
        for (SemesterEntity semester : semesterOptions) {
            labels.add(semesterDisplayName(semester));
        }
        actvExportSemester.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, labels));

        selectedSemester = findSemester(restoredSemesterId);
        if (selectedSemester == null) {
            for (SemesterEntity semester : semesterOptions) {
                if (semester.isCurrent) {
                    selectedSemester = semester;
                    break;
                }
            }
        }
        if (selectedSemester == null && !semesterOptions.isEmpty()) {
            selectedSemester = semesterOptions.get(0);
        }
        if (selectedSemester != null) {
            restoredSemesterId = selectedSemester.id;
            actvExportSemester.setText(semesterDisplayName(selectedSemester), false);
        } else {
            restoredSemesterId = -1L;
            actvExportSemester.setText("", false);
        }
        updateScopePreview();
        updateExportEnabled();
    }

    private SemesterEntity findSemester(long semesterId) {
        if (semesterId <= 0) return null;
        for (SemesterEntity semester : semesterOptions) {
            if (semester.id == semesterId) return semester;
        }
        return null;
    }

    private void loadSnapshot() {
        new Thread(() -> {
            try {
                ScheduleSnapshot loaded = repository.getScheduleSnapshot().get();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    snapshot = loaded == null ? new ScheduleSnapshot(null, null, null) : loaded;
                    schedulesLoaded = true;
                    updateScopePreview();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException exception) {
                mainHandler.post(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        schedulesLoaded = true;
                        tvScopeSummary.setText(R.string.export_preview_load_failed);
                    }
                });
            }
        }, "export-preview-load").start();
    }

    private static ExportSessions.Result collectSessions(ScheduleSnapshot source, SemesterEntity semester,
                                                         int scopeIndex, long from, long to,
                                                         boolean includeCancelled) {
        if (scopeIndex == SCOPE_CUSTOM) {
            return ExportSessions.collectRange(source, semester, from, to, includeCancelled);
        }
        return ExportSessions.collect(source, semester, SCOPES[scopeIndex],
                LocalDate.now(), includeCancelled);
    }

    private void updateScopePreview() {
        if (selectedSemester == null) {
            tvScopeDates.setText(R.string.export_preview_no_semester);
            tvScopeSummary.setText(R.string.export_preview_empty);
            return;
        }
        if (!schedulesLoaded) {
            tvScopeDates.setText(SCOPE_LABELS[selectedScopeIndex]);
            tvScopeSummary.setText(R.string.export_preview_loading);
            return;
        }

        ExportSessions.Result result;
        try {
            result = collectSessions(snapshot, selectedSemester, selectedScopeIndex,
                    customFrom, customTo, false);
        } catch (IllegalArgumentException exception) {
            tvScopeSummary.setText(R.string.export_preview_too_many);
            return;
        }
        if (result.range.isEmpty()) {
            tvScopeDates.setText(R.string.export_preview_no_dates);
            tvScopeSummary.setText(R.string.export_preview_empty);
            return;
        }
        tvScopeDates.setText(formatRange(result.range));
        Set<Long> subjectIds = new HashSet<>();
        for (ClassOccurrence session : result.sessions) {
            subjectIds.add(session.subjectId);
        }
        tvScopeSummary.setText(getString(R.string.export_preview_summary,
                result.sessions.size(), subjectIds.size()));
    }

    private void beginExport() {
        if (selectedSemester == null) {
            Toast.makeText(this, R.string.export_preview_no_semester, Toast.LENGTH_SHORT).show();
            return;
        }
        if (exporting) return;
        String name = buildFileName(selectedSemester, selectedScopeIndex, selectedFormat,
                customFrom, customTo);
        if (selectedFormat == FORMAT_PDF) {
            createPdfLauncher.launch(name);
        } else if (selectedFormat == FORMAT_EXCEL_SCHEDULE) {
            createExcelScheduleLauncher.launch(name);
        } else {
            createExcelSubjectsLauncher.launch(name);
        }
    }

    private void onFileLocationSelected(Uri uri, int format) {
        if (uri == null) return;
        if (exporting) return;

        final long semesterId = selectedSemester == null ? restoredSemesterId : selectedSemester.id;
        final int scopeIndex = selectedScopeIndex;
        final long from = customFrom;
        final long to = customTo;
        exporting = true;
        btnExport.setEnabled(false);
        btnExport.setText(R.string.export_working);

        new Thread(() -> {
            try {
                SemesterEntity semester = repository.getSemester(semesterId).get();
                if (semester == null) throw new IOException("Không tìm thấy học kỳ đã chọn.");
                ScheduleSnapshot current = repository.getScheduleSnapshot().get();
                // The timetable sheet lists cancelled sessions (marked); PDF and subject totals do not.
                ExportSessions.Result result = collectSessions(current, semester, scopeIndex,
                        from, to, format == FORMAT_EXCEL_SCHEDULE);

                if (format == FORMAT_PDF) {
                    try (OutputStream stream = openOutput(uri)) {
                        SchedulePdfWriter.write(stream, semester.name, semester.academicYear,
                                SCOPE_LABELS[scopeIndex].replace("…", ""), result.range,
                                ExportSessions.toPdfRows(result.sessions));
                    }
                } else {
                    // Build the whole workbook first so a failure never leaves a half-written file.
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    if (format == FORMAT_EXCEL_SCHEDULE) {
                        ScheduleExcelExporter.writeSchedule(buffer, result.sessions);
                    } else {
                        AppDatabase database = AppDatabase.getInstance(getApplicationContext());
                        List<SubjectEntity> subjects = new ArrayList<>();
                        for (SubjectEntity subject : database.subjectDao().getAll()) {
                            if (subject.semesterId == semesterId) subjects.add(subject);
                        }
                        List<ClassSectionEntity> sections = database.classSectionDao().getAll();
                        ScheduleExcelExporter.writeSubjects(buffer, subjects, sections, result.sessions);
                    }
                    try (OutputStream stream = openOutput(uri)) {
                        stream.write(buffer.toByteArray());
                        stream.flush();
                    }
                }
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setExporting(false);
                    Toast.makeText(this, R.string.export_saved, Toast.LENGTH_LONG).show();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                finishExportWithError();
            } catch (ExecutionException | IOException | RuntimeException exception) {
                finishExportWithError();
            }
        }, "schedule-export").start();
    }

    private OutputStream openOutput(Uri uri) throws IOException {
        OutputStream output = getContentResolver().openOutputStream(uri, "wt");
        if (output == null) throw new IOException("Không thể mở tệp đích.");
        return output;
    }

    private void finishExportWithError() {
        mainHandler.post(() -> {
            if (isFinishing() || isDestroyed()) return;
            setExporting(false);
            Toast.makeText(this, R.string.export_failed, Toast.LENGTH_LONG).show();
        });
    }

    private void setExporting(boolean value) {
        exporting = value;
        btnExport.setText(value ? R.string.export_working : R.string.action_export);
        updateExportEnabled();
    }

    private void updateExportEnabled() {
        if (btnExport != null) btnExport.setEnabled(!exporting && selectedSemester != null);
    }

    private void selectFormat(int format) {
        selectedFormat = format;
        int primaryColor = ContextCompat.getColor(this, R.color.primary);
        int borderColor = ContextCompat.getColor(this, R.color.border_light);

        cardExportPdf.setStrokeColor(format == FORMAT_PDF ? primaryColor : borderColor);
        cardExportPdf.setStrokeWidth(format == FORMAT_PDF ? 4 : 2);
        rbExportPdf.setChecked(format == FORMAT_PDF);

        cardExportExcelSchedule.setStrokeColor(format == FORMAT_EXCEL_SCHEDULE ? primaryColor : borderColor);
        cardExportExcelSchedule.setStrokeWidth(format == FORMAT_EXCEL_SCHEDULE ? 4 : 2);
        rbExportExcelSchedule.setChecked(format == FORMAT_EXCEL_SCHEDULE);

        cardExportExcelSubjects.setStrokeColor(format == FORMAT_EXCEL_SUBJECTS ? primaryColor : borderColor);
        cardExportExcelSubjects.setStrokeWidth(format == FORMAT_EXCEL_SUBJECTS ? 4 : 2);
        rbExportExcelSubjects.setChecked(format == FORMAT_EXCEL_SUBJECTS);
    }

    private static String semesterDisplayName(SemesterEntity semester) {
        if (semester == null) return "";
        return (semester.name == null ? "Học kỳ" : semester.name)
                + (semester.academicYear == null || semester.academicYear.trim().isEmpty()
                ? "" : " · " + semester.academicYear);
    }

    private static String formatRange(ScheduleExportCalculator.DateRange range) {
        return PREVIEW_DATE_FORMAT.format(LocalDate.ofEpochDay(range.startEpochDay))
                + " – " + PREVIEW_DATE_FORMAT.format(LocalDate.ofEpochDay(range.endEpochDay));
    }

    private static String buildFileName(SemesterEntity semester, int scopeIndex, int format,
                                        long from, long to) {
        String semesterName = semester.name == null ? "hoc-ky" : semester.name;
        semesterName = semesterName.trim().replaceAll("[^\\p{L}\\p{N}-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (semesterName.isEmpty()) semesterName = "hoc-ky";
        String scope;
        if (scopeIndex == SCOPE_CUSTOM) {
            scope = LocalDate.ofEpochDay(from) + "_" + LocalDate.ofEpochDay(to);
        } else {
            scope = scopeIndex == 0 ? "tuan" : scopeIndex == 1 ? "thang" : "hoc-ky";
        }
        if (format == FORMAT_PDF) {
            return "thoi-khoa-bieu-" + semesterName + "-" + scope + ".pdf";
        }
        if (format == FORMAT_EXCEL_SCHEDULE) {
            return "thoi-khoa-bieu-" + semesterName + "-" + scope + ".xlsx";
        }
        return "mon-hoc-" + semesterName + "-" + scope + ".xlsx";
    }
}