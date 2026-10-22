package com.example.demo.ui.stats;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.demo.R;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.validation.SemesterStatisticsCalculator;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class StatsActivity extends AppCompatActivity {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int MAX_DAY_BAR_HEIGHT_DP = 90;

    private MaterialButton btnFilterSemester;
    private MaterialButton btnFilterRange;
    private TextView tvKpiSubjects;
    private TextView tvKpiClassesPerWeek;
    private TextView tvKpiHoursPerWeek;
    private TextView tvStatsEmpty;
    private TextView tvSemesterVolumesEmpty;
    private LinearLayout layoutSemesterVolumes;
    private final View[] weekdayBars = new View[7];
    private final TextView[] weekdayCounts = new TextView[7];

    private final List<SemesterSummary> semesterSummaries = new ArrayList<>();
    private List<ScheduleDetails> schedules = new ArrayList<>();
    private boolean schedulesLoaded;
    private long selectedSemesterId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        btnFilterSemester.setOnClickListener(v -> showSemesterPicker());
        // This pill displays the real date span of the selected semester; it is informational.
        btnFilterRange.setClickable(false);
        btnFilterRange.setFocusable(false);

        AppRepository repository = AppRepository.getInstance(getApplicationContext());
        repository.observeSemesterSummaries().observe(this, this::onSemesterSummariesChanged);
        repository.observeAllScheduleDetails().observe(this, values -> {
            schedules = values == null ? new ArrayList<>() : values;
            schedulesLoaded = true;
            renderStatistics();
        });
    }

    private void bindViews() {
        btnFilterSemester = findViewById(R.id.btnFilterSemester);
        btnFilterRange = findViewById(R.id.btnFilterMonth);
        tvKpiSubjects = findViewById(R.id.tvKpiSubjects);
        tvKpiClassesPerWeek = findViewById(R.id.tvKpiClassesPerWeek);
        tvKpiHoursPerWeek = findViewById(R.id.tvKpiHoursPerWeek);
        tvStatsEmpty = findViewById(R.id.tvStatsEmpty);
        tvSemesterVolumesEmpty = findViewById(R.id.tvSemesterVolumesEmpty);
        layoutSemesterVolumes = findViewById(R.id.layoutSemesterVolumes);

        int[] barIds = {
                R.id.barDay1, R.id.barDay2, R.id.barDay3, R.id.barDay4,
                R.id.barDay5, R.id.barDay6, R.id.barDay7
        };
        int[] countIds = {
                R.id.tvCountDay1, R.id.tvCountDay2, R.id.tvCountDay3, R.id.tvCountDay4,
                R.id.tvCountDay5, R.id.tvCountDay6, R.id.tvCountDay7
        };
        for (int index = 0; index < 7; index++) {
            weekdayBars[index] = findViewById(barIds[index]);
            weekdayCounts[index] = findViewById(countIds[index]);
        }
    }

    private void onSemesterSummariesChanged(List<SemesterSummary> values) {
        semesterSummaries.clear();
        if (values != null) {
            semesterSummaries.addAll(values);
        }

        SemesterSummary selected = findSelectedSemester();
        if (selected == null && !semesterSummaries.isEmpty()) {
            selected = chooseDefaultSemester();
            selectedSemesterId = selected.semester.id;
        } else if (selected == null) {
            selectedSemesterId = 0L;
        }
        renderStatistics();
    }

    private void showSemesterPicker() {
        if (semesterSummaries.isEmpty()) {
            return;
        }
        String[] labels = new String[semesterSummaries.size()];
        int selectedPosition = 0;
        for (int index = 0; index < semesterSummaries.size(); index++) {
            SemesterEntity semester = semesterSummaries.get(index).semester;
            labels[index] = formatSemester(semester);
            if (semester.id == selectedSemesterId) {
                selectedPosition = index;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.stats_semester_picker_title)
                .setSingleChoiceItems(labels, selectedPosition, (dialog, which) -> {
                    if (which >= 0 && which < semesterSummaries.size()) {
                        selectedSemesterId = semesterSummaries.get(which).semester.id;
                        renderStatistics();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void renderStatistics() {
        SemesterSummary selected = findSelectedSemester();
        if (selected == null) {
            renderNoSemester();
            updateSemesterVolumes();
            return;
        }

        SemesterEntity semester = selected.semester;
        btnFilterSemester.setEnabled(true);
        btnFilterSemester.setText(formatSemester(semester));
        btnFilterRange.setText(formatSemesterRange(semester));

        SemesterStatisticsCalculator.Statistics statistics = SemesterStatisticsCalculator.calculate(
                semester, selected.subjectCount, schedules);
        tvKpiSubjects.setText(String.valueOf(statistics.subjectCount));
        tvKpiClassesPerWeek.setText(String.valueOf(statistics.classesPerWeek));
        tvKpiHoursPerWeek.setText(formatHours(statistics.weeklyMinutes));
        tvKpiSubjects.setContentDescription(getString(
                R.string.subjects_count_format, statistics.subjectCount));
        tvKpiClassesPerWeek.setContentDescription(getString(
                R.string.stats_classes_per_week_format, statistics.classesPerWeek));
        tvKpiHoursPerWeek.setContentDescription(getString(
                R.string.stats_hours_per_week_content_description,
                formatHours(statistics.weeklyMinutes)));
        updateWeekdayChart(statistics.classesByDay);

        if (schedulesLoaded && statistics.classesPerWeek == 0) {
            tvStatsEmpty.setText(R.string.stats_no_schedule_message);
            tvStatsEmpty.setVisibility(View.VISIBLE);
        } else {
            tvStatsEmpty.setVisibility(View.GONE);
        }
        updateSemesterVolumes();
    }

    private void renderNoSemester() {
        btnFilterSemester.setEnabled(false);
        btnFilterSemester.setText(R.string.stats_no_semester);
        btnFilterRange.setText(R.string.stats_full_semester_range);
        tvKpiSubjects.setText("0");
        tvKpiClassesPerWeek.setText("0");
        tvKpiHoursPerWeek.setText(formatHours(0L));
        tvKpiSubjects.setContentDescription(getString(R.string.subjects_empty_count));
        tvKpiClassesPerWeek.setContentDescription(getString(
                R.string.stats_classes_per_week_format, 0));
        tvKpiHoursPerWeek.setContentDescription(getString(
                R.string.stats_hours_per_week_content_description, formatHours(0L)));
        tvStatsEmpty.setText(R.string.stats_no_semester_message);
        tvStatsEmpty.setVisibility(View.VISIBLE);
        updateWeekdayChart(new int[7]);
    }

    private void updateWeekdayChart(int[] countsByDay) {
        int maxCount = 0;
        for (int count : countsByDay) {
            maxCount = Math.max(maxCount, count);
        }
        int maxHeight = dpToPx(MAX_DAY_BAR_HEIGHT_DP);
        String[] dayLabels = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};
        int primary = ContextCompat.getColor(this, R.color.primary);
        int emptyBar = ContextCompat.getColor(this, R.color.border_light);
        for (int index = 0; index < countsByDay.length; index++) {
            int count = countsByDay[index];
            weekdayCounts[index].setText(String.valueOf(count));
            int barHeight = count == 0 || maxCount == 0
                    ? 0 : Math.max(dpToPx(4), Math.round(maxHeight * (count / (float) maxCount)));
            weekdayBars[index].getLayoutParams().height = barHeight;
            weekdayBars[index].setBackgroundTintList(ColorStateList.valueOf(
                    count == 0 ? emptyBar : primary));
            weekdayBars[index].setContentDescription(getString(
                    R.string.stats_day_chart_content_description, dayLabels[index], count));
            weekdayBars[index].requestLayout();
        }
    }

    private void updateSemesterVolumes() {
        layoutSemesterVolumes.removeAllViews();
        tvSemesterVolumesEmpty.setVisibility(
                semesterSummaries.isEmpty() ? View.VISIBLE : View.GONE);
        if (semesterSummaries.isEmpty()) {
            return;
        }

        List<SemesterStatisticsCalculator.Statistics> volumes = new ArrayList<>();
        long maxMinutes = 0L;
        for (SemesterSummary summary : semesterSummaries) {
            SemesterStatisticsCalculator.Statistics stats = SemesterStatisticsCalculator.calculate(
                    summary.semester, summary.subjectCount, schedules);
            volumes.add(stats);
            maxMinutes = Math.max(maxMinutes, stats.totalSemesterMinutes);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int primary = ContextCompat.getColor(this, R.color.primary);
        for (int index = 0; index < semesterSummaries.size(); index++) {
            SemesterSummary summary = semesterSummaries.get(index);
            SemesterStatisticsCalculator.Statistics stats = volumes.get(index);
            View row = inflater.inflate(R.layout.item_stats_semester_volume,
                    layoutSemesterVolumes, false);
            String label = formatSemesterShort(summary.semester);
            String hours = formatHours(stats.totalSemesterMinutes);
            ((TextView) row.findViewById(R.id.tvSemesterVolumeLabel)).setText(label);
            TextView volumeValue = row.findViewById(R.id.tvSemesterVolumeHours);
            volumeValue.setText(hours);

            ProgressBar progress = row.findViewById(R.id.progressSemesterVolume);
            progress.setProgressTintList(ColorStateList.valueOf(primary));
            int percent = maxMinutes == 0L ? 0
                    : (int) Math.round(stats.totalSemesterMinutes * 100.0 / maxMinutes);
            progress.setProgress(percent);
            progress.setContentDescription(getString(
                    R.string.stats_semester_volume_content_description, label, hours));
            layoutSemesterVolumes.addView(row);
        }
    }

    private SemesterSummary findSelectedSemester() {
        for (SemesterSummary summary : semesterSummaries) {
            if (summary.semester.id == selectedSemesterId) {
                return summary;
            }
        }
        return null;
    }

    private SemesterSummary chooseDefaultSemester() {
        for (SemesterSummary summary : semesterSummaries) {
            if (summary.semester.isCurrent) {
                return summary;
            }
        }
        return semesterSummaries.get(0);
    }

    private String formatSemester(SemesterEntity semester) {
        if (isBlank(semester.academicYear)) {
            return safe(semester.name);
        }
        return getString(R.string.semester_label_format, safe(semester.name), semester.academicYear);
    }

    private String formatSemesterShort(SemesterEntity semester) {
        String name = safe(semester.name).trim();
        String normalized = name.toLowerCase(Locale.ROOT);
        if (normalized.contains("học kỳ 1") || normalized.startsWith("hk1")) {
            name = "HK1";
        } else if (normalized.contains("học kỳ 2") || normalized.startsWith("hk2")) {
            name = "HK2";
        } else if (normalized.contains("học kỳ 3") || normalized.startsWith("hk3")) {
            name = "HK3";
        }

        String year = safe(semester.academicYear).replaceAll("[^0-9]", "");
        if (year.length() >= 8) {
            return name + " " + year.substring(2, 4) + "–" + year.substring(6, 8);
        }
        return name;
    }

    private String formatSemesterRange(SemesterEntity semester) {
        Long start = semester.startDateEpochDay;
        Long end = semester.endDateEpochDay;
        if (start == null && end == null) {
            return getString(R.string.stats_full_semester_range);
        }
        if (start == null) {
            return getString(R.string.stats_date_range_until, formatDate(end));
        }
        if (end == null) {
            return getString(R.string.stats_date_range_from, formatDate(start));
        }
        return getString(R.string.stats_date_range_format, formatDate(start), formatDate(end));
    }

    private String formatHours(long minutes) {
        long fullHours = minutes / 60L;
        long remainingMinutes = Math.abs(minutes % 60L);
        String value = remainingMinutes == 0L
                ? String.valueOf(fullHours)
                : String.format(Locale.getDefault(), "%.1f", minutes / 60.0);
        return value + getString(R.string.stats_hours_abbreviation);
    }

    private String formatDate(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DATE_FORMAT);
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
