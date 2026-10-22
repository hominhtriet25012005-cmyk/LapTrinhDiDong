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

import com.example.demo.R;
import com.example.demo.data.export.ScheduleExportCalculator;
import com.example.demo.data.export.SchedulePdfWriter;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class ExportActivity extends AppCompatActivity {
    private static final String STATE_SEMESTER_ID = "export_semester_id";
    private static final String STATE_SCOPE = "export_scope";
    private static final String[] SCOPE_LABELS = {
            "Tuần hiện tại", "Tháng hiện tại", "Toàn bộ học kỳ"
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
    private final List<ScheduleDetails> allScheduleDetails = new ArrayList<>();
    private AppRepository repository;
    private ActivityResultLauncher<String> createPdfLauncher;
    private SemesterEntity selectedSemester;
    private long restoredSemesterId = -1L;
    private int selectedFormat = 0; // 0: PDF Schedule, 1: Excel Schedule, 2: Excel Subjects
    private int selectedScopeIndex = 0;
    private boolean schedulesLoaded;
    private boolean exporting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_export);

        if (savedInstanceState != null) {
            restoredSemesterId = savedInstanceState.getLong(STATE_SEMESTER_ID, -1L);
            selectedScopeIndex = savedInstanceState.getInt(STATE_SCOPE, 0);
        }
        if (selectedScopeIndex < 0 || selectedScopeIndex >= SCOPES.length) selectedScopeIndex = 0;

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
                new ActivityResultContracts.CreateDocument("application/pdf"), this::onPdfLocationSelected);

        setupDropdowns();
        cardExportPdf.setOnClickListener(v -> selectFormat(0));
        cardExportExcelSchedule.setOnClickListener(v -> selectFormat(1));
        cardExportExcelSubjects.setOnClickListener(v -> selectFormat(2));
        btnExport.setOnClickListener(v -> beginExport());

        repository.observeSemesters().observe(this, this::onSemestersChanged);
        loadScheduleDetails();
        selectFormat(0);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putLong(STATE_SEMESTER_ID, selectedSemester == null
                ? restoredSemesterId : selectedSemester.id);
        outState.putInt(STATE_SCOPE, selectedScopeIndex);
        super.onSaveInstanceState(outState);
    }

    private void setupDropdowns() {
        ArrayAdapter<String> scopeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_dropdown_item_1line, SCOPE_LABELS);
        actvExportScope.setAdapter(scopeAdapter);
        actvExportScope.setText(SCOPE_LABELS[selectedScopeIndex], false);
        actvExportScope.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < SCOPES.length) {
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

    private void loadScheduleDetails() {
        new Thread(() -> {
            try {
                List<ScheduleDetails> details = repository.getAllScheduleDetails().get();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    allScheduleDetails.clear();
                    if (details != null) allScheduleDetails.addAll(details);
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

        List<ScheduleDetails> semesterSchedules = schedulesForSelectedSemester(allScheduleDetails,
                selectedSemester.id);
        ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                SCOPES[selectedScopeIndex], LocalDate.now(), selectedSemester, semesterSchedules);
        if (range.isEmpty()) {
            tvScopeDates.setText(R.string.export_preview_no_dates);
            tvScopeSummary.setText(R.string.export_preview_empty);
            return;
        }
        tvScopeDates.setText(formatRange(range));
        List<ScheduleExportCalculator.Occurrence> occurrences;
        try {
            occurrences = ScheduleExportCalculator.occurrences(
                    selectedSemester.id, semesterSchedules, range);
        } catch (IllegalArgumentException exception) {
            tvScopeSummary.setText(R.string.export_preview_too_many);
            return;
        }
        Set<Long> subjectIds = new HashSet<>();
        for (ScheduleExportCalculator.Occurrence occurrence : occurrences) {
            subjectIds.add(occurrence.schedule.subjectId);
        }
        tvScopeSummary.setText(getString(R.string.export_preview_summary,
                occurrences.size(), subjectIds.size()));
    }

    private void beginExport() {
        if (selectedFormat != 0) {
            Toast.makeText(this, R.string.export_excel_unavailable, Toast.LENGTH_LONG).show();
            return;
        }
        if (selectedSemester == null) {
            Toast.makeText(this, R.string.export_preview_no_semester, Toast.LENGTH_SHORT).show();
            return;
        }
        if (exporting) return;
        createPdfLauncher.launch(buildFileName(selectedSemester, selectedScopeIndex));
    }

    private void onPdfLocationSelected(Uri uri) {
        if (uri == null) return;
        if (exporting) return;

        final long semesterId = selectedSemester == null ? restoredSemesterId : selectedSemester.id;
        final int scopeIndex = selectedScopeIndex;
        exporting = true;
        btnExport.setEnabled(false);
        btnExport.setText(R.string.export_working);

        new Thread(() -> {
            try {
                SemesterEntity semester = repository.getSemester(semesterId).get();
                if (semester == null) throw new IOException("Không tìm thấy học kỳ đã chọn.");
                List<ScheduleDetails> details = repository.getAllScheduleDetails().get();
                List<ScheduleDetails> semesterSchedules = schedulesForSelectedSemester(details, semesterId);
                ScheduleExportCalculator.DateRange range = ScheduleExportCalculator.resolveRange(
                        SCOPES[scopeIndex], LocalDate.now(), semester, semesterSchedules);
                List<ScheduleExportCalculator.Occurrence> occurrences =
                        ScheduleExportCalculator.occurrences(semesterId, semesterSchedules, range);
                OutputStream output = getContentResolver().openOutputStream(uri, "w");
                if (output == null) throw new IOException("Không thể mở tệp đích.");
                try (OutputStream stream = output) {
                    SchedulePdfWriter.write(stream, semester.name, semester.academicYear,
                            SCOPE_LABELS[scopeIndex], range, occurrences);
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
        }, "schedule-pdf-export").start();
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

        cardExportPdf.setStrokeColor(format == 0 ? primaryColor : borderColor);
        cardExportPdf.setStrokeWidth(format == 0 ? 4 : 2);
        rbExportPdf.setChecked(format == 0);

        cardExportExcelSchedule.setStrokeColor(format == 1 ? primaryColor : borderColor);
        cardExportExcelSchedule.setStrokeWidth(format == 1 ? 4 : 2);
        rbExportExcelSchedule.setChecked(format == 1);

        cardExportExcelSubjects.setStrokeColor(format == 2 ? primaryColor : borderColor);
        cardExportExcelSubjects.setStrokeWidth(format == 2 ? 4 : 2);
        rbExportExcelSubjects.setChecked(format == 2);
    }

    private static List<ScheduleDetails> schedulesForSelectedSemester(List<ScheduleDetails> details,
                                                                       long semesterId) {
        List<ScheduleDetails> result = new ArrayList<>();
        if (details == null) return result;
        for (ScheduleDetails detail : details) {
            if (detail != null && detail.semesterId == semesterId) result.add(detail);
        }
        return result;
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

    private static String buildFileName(SemesterEntity semester, int scopeIndex) {
        String semesterName = semester.name == null ? "hoc-ky" : semester.name;
        semesterName = semesterName.trim().replaceAll("[^\\p{L}\\p{N}-]+", "-")
                .replaceAll("^-+|-+$", "");
        if (semesterName.isEmpty()) semesterName = "hoc-ky";
        String scope = scopeIndex == 0 ? "tuan" : scopeIndex == 1 ? "thang" : "hoc-ky";
        return "thoi-khoa-bieu-" + semesterName + "-" + scope + ".pdf";
    }
}
