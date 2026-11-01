package com.example.demo.ui.stats;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.example.demo.R;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.schedule.ScheduleSnapshot;
import com.example.demo.data.schedule.SessionStatistics;
import com.example.demo.data.settings.WeekSettings;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class StatsActivity extends AppCompatActivity {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM");
    private static final int MAX_DAY_BAR_HEIGHT_DP = 90;
    private static final int RANGE_SEMESTER = 0;
    private static final int RANGE_WEEK = 1;
    private static final int RANGE_MONTH = 2;
    private static final int RANGE_CUSTOM = 3;
    private static final String[] DAY_LABELS = {"T2", "T3", "T4", "T5", "T6", "T7", "CN"};

    private MaterialButton btnFilterSemester;
    private MaterialButton btnFilterRange;
    private TextView tvKpiSubjects;
    private TextView tvKpiClassesPerWeek;
    private TextView tvKpiHoursPerWeek;
    private TextView tvStatsSummary;
    private TextView tvStatsHoursByDay;
    private TextView tvStatsEmpty;
    private TextView tvSemesterVolumesEmpty;
    private LinearLayout layoutSemesterVolumes;
    private final View[] weekdayBars = new View[7];
    private final TextView[] weekdayCounts = new TextView[7];

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SemesterSummary> semesterSummaries = new ArrayList<>();
    private AppRepository repository;
    private ScheduleSnapshot snapshot = SessionStatistics.emptySnapshot();
    private boolean snapshotLoaded;
    private long selectedSemesterId;
    private int rangeMode = RANGE_SEMESTER;
    private long customFrom;
    private long customTo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stats);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        bindViews();
        btnFilterSemester.setOnClickListener(v -> showSemesterPicker());
        btnFilterRange.setOnClickListener(v -> showRangePicker());

        repository = AppRepository.getInstance(getApplicationContext());
        repository.observeSemesterSummaries().observe(this, this::onSemesterSummariesChanged);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadSnapshot();
    }

    private void loadSnapshot() {
        new Thread(() -> {
            try {
                ScheduleSnapshot loaded = repository.getScheduleSnapshot().get();
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    snapshot = loaded == null ? SessionStatistics.emptySnapshot() : loaded;
                    snapshotLoaded = true;
                    renderStatistics();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException exception) {
                // Keep showing the previous numbers.
            }
        }, "stats-load").start();
    }

    private void bindViews() {
        btnFilterSemester = findViewById(R.id.btnFilterSemester);
        btnFilterRange = findViewById(R.id.btnFilterMonth);
        tvKpiSubjects = findViewById(R.id.tvKpiSubjects);
        tvKpiClassesPerWeek = findViewById(R.id.tvKpiClassesPerWeek);
        tvKpiHoursPerWeek = findViewById(R.id.tvKpiHoursPerWeek);
        tvStatsSummary = findViewById(R.id.tvStatsSummary);
        tvStatsHoursByDay = findViewById(R.id.tvStatsHoursByDay);
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

    private void showRangePicker() {
        if (findSelectedSemester() == null) {
            return;
        }
        String[] labels = {
                getString(R.string.stats_full_semester_range),
                getString(R.string.stats_range_week),
                getString(R.string.stats_range_month),
                getString(R.string.stats_range_custom)
        };
        new AlertDialog.Builder(this)
                .setTitle(R.string.stats_range_picker_title)
                .setSingleChoiceItems(labels, rangeMode, (dialog, which) -> {
                    dialog.dismiss();
                    if (which == RANGE_CUSTOM) {
                        showCustomRangePicker();
                    } else {
                        rangeMode = which;
                        renderStatistics();
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showCustomRangePicker() {
        long today = LocalDate.now().toEpochDay();
        long from = rangeMode == RANGE_CUSTOM ? customFrom : today;
        long to = rangeMode == RANGE_CUSTOM ? customTo : today;
        MaterialDatePicker<Pair<Long, Long>> picker = MaterialDatePicker.Builder.dateRangePicker()
                .setTitleText(R.string.stats_range_custom)
                .setSelection(new Pair<>(from * 86_400_000L, to * 86_400_000L))
                .build();
        picker.addOnPositiveButtonClickListener(selection -> {
            if (selection == null || selection.first == null || selection.second == null) return;
            customFrom = Instant.ofEpochMilli(selection.first).atZone(ZoneOffset.UTC)
                    .toLocalDate().toEpochDay();
            customTo = Instant.ofEpochMilli(selection.second).atZone(ZoneOffset.UTC)
                    .toLocalDate().toEpochDay();
            rangeMode = RANGE_CUSTOM;
            renderStatistics();
        });
        picker.show(getSupportFragmentManager(), "stats_range");
    }

    /** The date range to measure, or null when the semester has no dates and no schedules. */
    private long[] currentRange(SemesterEntity semester) {
        LocalDate today = LocalDate.now();
        if (rangeMode == RANGE_WEEK) {
            LocalDate start = WeekSettings.weekStart(today);
            return new long[]{start.toEpochDay(), start.plusDays(6).toEpochDay()};
        }
        if (rangeMode == RANGE_MONTH) {
            LocalDate first = today.withDayOfMonth(1);
            return new long[]{first.toEpochDay(), first.plusMonths(1).minusDays(1).toEpochDay()};
        }
        if (rangeMode == RANGE_CUSTOM) {
            return new long[]{customFrom, customTo};
        }
        return SessionStatistics.semesterSpan(snapshot, semester);
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

        long[] range = currentRange(semester);
        SessionStatistics.Result stats = range == null
                ? SessionStatistics.calculate(snapshot, semester, 1L, 0L)
                : SessionStatistics.calculate(snapshot, semester, range[0], range[1]);
        btnFilterRange.setText(rangeLabel(semester, stats));

        tvKpiSubjects.setText(String.valueOf(selected.subjectCount));
        tvKpiClassesPerWeek.setText(formatNumber(stats.sessionsPerWeek));
        tvKpiHoursPerWeek.setText(formatHours(Math.round(stats.minutesPerWeek)));
        tvKpiSubjects.setContentDescription(getString(
                R.string.subjects_count_format, selected.subjectCount));
        tvKpiClassesPerWeek.setContentDescription(getString(
                R.string.stats_classes_per_week_format, (int) Math.round(stats.sessionsPerWeek)));
        tvKpiHoursPerWeek.setContentDescription(getString(
                R.string.stats_hours_per_week_content_description,
                formatHours(Math.round(stats.minutesPerWeek))));
        updateWeekdayChart(stats.sessionsByDay);

        tvStatsSummary.setText(getString(R.string.stats_summary_format, stats.sessionCount,
                formatHours(stats.totalMinutes), stats.cancelledCount, stats.modifiedCount,
                stats.makeupCount));
        tvStatsHoursByDay.setText(getString(R.string.stats_hours_by_day_format,
                hoursByDayText(stats.minutesByDay)));
        tvStatsSummary.setVisibility(View.VISIBLE);
        tvStatsHoursByDay.setVisibility(View.VISIBLE);

        if (snapshotLoaded && stats.sessionCount == 0) {
            tvStatsEmpty.setText(R.string.stats_no_sessions_message);
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
        tvStatsSummary.setVisibility(View.GONE);
        tvStatsHoursByDay.setVisibility(View.GONE);
        tvStatsEmpty.setText(R.string.stats_no_semester_message);
        tvStatsEmpty.setVisibility(View.VISIBLE);
        updateWeekdayChart(new int[7]);
    }

    private String rangeLabel(SemesterEntity semester, SessionStatistics.Result stats) {
        if (rangeMode == RANGE_SEMESTER) {
            return formatSemesterRange(semester);
        }
        if (stats.emptyRange) {
            return getString(R.string.stats_range_none);
        }
        return getString(R.string.stats_date_range_format,
                formatDate(stats.fromEpochDay), formatDate(stats.toEpochDay));
    }

    private String hoursByDayText(long[] minutesByDay) {
        StringBuilder text = new StringBuilder();
        for (int index = 0; index < 7; index++) {
            if (index > 0) text.append(" · ");
            text.append(DAY_LABELS[index]).append(' ').append(formatHours(minutesByDay[index]));
        }
        return text.toString();
    }

    private void updateWeekdayChart(int[] countsByDay) {
        int maxCount = 0;
        for (int count : countsByDay) {
            maxCount = Math.max(maxCount, count);
        }
        int maxHeight = dpToPx(MAX_DAY_BAR_HEIGHT_DP);
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
                    R.string.stats_day_chart_content_description, DAY_LABELS[index], count));
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

        List<Long> volumes = new ArrayList<>();
        long maxMinutes = 0L;
        for (SemesterSummary summary : semesterSummaries) {
            long[] span = SessionStatistics.semesterSpan(snapshot, summary.semester);
            long minutes = span == null ? 0L : SessionStatistics.calculate(
                    snapshot, summary.semester, span[0], span[1]).totalMinutes;
            volumes.add(minutes);
            maxMinutes = Math.max(maxMinutes, minutes);
        }

        LayoutInflater inflater = LayoutInflater.from(this);
        int primary = ContextCompat.getColor(this, R.color.primary);
        for (int index = 0; index < semesterSummaries.size(); index++) {
            SemesterSummary summary = semesterSummaries.get(index);
            long minutes = volumes.get(index);
            View row = inflater.inflate(R.layout.item_stats_semester_volume,
                    layoutSemesterVolumes, false);
            String label = formatSemesterShort(summary.semester);
            String hours = formatHours(minutes);
            ((TextView) row.findViewById(R.id.tvSemesterVolumeLabel)).setText(label);
            ((TextView) row.findViewById(R.id.tvSemesterVolumeHours)).setText(hours);

            ProgressBar progress = row.findViewById(R.id.progressSemesterVolume);
            progress.setProgressTintList(ColorStateList.valueOf(primary));
            int percent = maxMinutes == 0L ? 0
                    : (int) Math.round(minutes * 100.0 / maxMinutes);
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

    private String formatNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.05) {
            return String.valueOf((long) Math.rint(value));
        }
        return String.format(Locale.getDefault(), "%.1f", value);
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