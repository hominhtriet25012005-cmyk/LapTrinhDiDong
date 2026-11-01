package com.example.demo.ui.timetable;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.example.demo.data.settings.WeekSettings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.adapter.ScheduleAdapter;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;
import com.example.demo.model.ScheduleItem;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class TimetableFragment extends Fragment {
    private static final int MODE_DAY = 0;
    private static final int MODE_WEEK = 1;
    private static final int MODE_MONTH = 2;

    private MaterialButtonToggleGroup toggleViewMode;
    private MaterialButton btnModeDay;
    private MaterialButton btnModeWeek;
    private MaterialButton btnModeMonth;
    private TextView tvSelectedDate;
    private TextView tvTodayLabel;
    private TextView tvClassCountSubtitle;
    private TextView tvEmptySchedule;
    private RecyclerView rvTimetableClasses;
    private ScheduleAdapter scheduleAdapter;
    private AppRepository repository;
    private ScheduleSnapshot snapshot = new ScheduleSnapshot(null, null, null);
    private LocalDate selectedDate = LocalDate.now();
    private int viewMode = MODE_DAY;
    private int loadGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_timetable, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        toggleViewMode = view.findViewById(R.id.toggleViewMode);
        btnModeDay = view.findViewById(R.id.btnModeDay);
        btnModeWeek = view.findViewById(R.id.btnModeWeek);
        btnModeMonth = view.findViewById(R.id.btnModeMonth);
        tvSelectedDate = view.findViewById(R.id.tvSelectedDate);
        tvTodayLabel = view.findViewById(R.id.tvTodayLabel);
        tvClassCountSubtitle = view.findViewById(R.id.tvClassCountSubtitle);
        tvEmptySchedule = view.findViewById(R.id.tvEmptySchedule);
        rvTimetableClasses = view.findViewById(R.id.rvTimetableClasses);

        scheduleAdapter = new ScheduleAdapter(new ArrayList<>(), this::onScheduleItemClick);
        rvTimetableClasses.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTimetableClasses.setAdapter(scheduleAdapter);

        setupToggleGroup();
        view.findViewById(R.id.btnPrevDate).setOnClickListener(v -> movePeriod(-1));
        view.findViewById(R.id.btnNextDate).setOnClickListener(v -> movePeriod(1));
        view.findViewById(R.id.layoutCenterDate).setOnClickListener(v -> {
            selectedDate = LocalDate.now();
            updateDateDisplay();
            renderSchedules();
        });
        view.findViewById(R.id.btnSearch).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSearch();
            }
        });
        ExtendedFloatingActionButton fabAddSchedule = view.findViewById(R.id.fabAddSchedule);
        fabAddSchedule.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openAddSchedule();
            }
        });
        view.findViewById(R.id.fabAddMakeup).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openNewMakeup(selectedDate.toEpochDay());
            }
        });
        updateDateDisplay();
        resetToggleButtonsStyle();
        styleActiveButton(btnModeDay);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null && rvTimetableClasses != null) {
            loadSnapshot();
        }
    }

    private void onScheduleItemClick(ScheduleItem item) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openOccurrence(item);
        }
    }

    private void setupToggleGroup() {
        toggleViewMode.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) {
                return;
            }
            if (checkedId == R.id.btnModeDay) {
                viewMode = MODE_DAY;
                styleActiveButton(btnModeDay);
            } else if (checkedId == R.id.btnModeWeek) {
                viewMode = MODE_WEEK;
                styleActiveButton(btnModeWeek);
            } else if (checkedId == R.id.btnModeMonth) {
                viewMode = MODE_MONTH;
                styleActiveButton(btnModeMonth);
            }
            resetInactiveButtonsStyle();
            updateDateDisplay();
            renderSchedules();
        });
    }

    private void movePeriod(int amount) {
        if (viewMode == MODE_DAY) {
            selectedDate = selectedDate.plusDays(amount);
        } else if (viewMode == MODE_WEEK) {
            selectedDate = selectedDate.plusWeeks(amount);
        } else {
            selectedDate = selectedDate.plusMonths(amount);
        }
        updateDateDisplay();
        renderSchedules();
    }

    private void updateDateDisplay() {
        Locale vietnamese = new Locale("vi", "VN");
        if (viewMode == MODE_DAY) {
            tvSelectedDate.setText(selectedDate.format(
                    DateTimeFormatter.ofPattern("EEEE, d 'tháng' M 'năm' yyyy", vietnamese)));
        } else if (viewMode == MODE_WEEK) {
            LocalDate weekStart = WeekSettings.weekStart(selectedDate);
            LocalDate weekEnd = weekStart.plusDays(6);
            String start = weekStart.format(DateTimeFormatter.ofPattern("dd/MM", vietnamese));
            String end = weekEnd.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", vietnamese));
            tvSelectedDate.setText(getString(R.string.schedule_week_format, start, end));
        } else {
            String month = selectedDate.format(DateTimeFormatter.ofPattern("M 'năm' yyyy", vietnamese));
            tvSelectedDate.setText(getString(R.string.schedule_month_format, month));
        }
        tvTodayLabel.setVisibility(selectedDate.equals(LocalDate.now()) ? View.VISIBLE : View.GONE);
    }

    private void loadSnapshot() {
        int generation = ++loadGeneration;
        MainActivity activity = getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
        if (activity == null) {
            return;
        }
        Future<ScheduleSnapshot> load = repository.getScheduleSnapshot();
        new Thread(() -> {
            try {
                ScheduleSnapshot result = load.get();
                activity.runOnUiThread(() -> {
                    if (!isAdded() || generation != loadGeneration) {
                        return;
                    }
                    snapshot = result == null ? new ScheduleSnapshot(null, null, null) : result;
                    renderSchedules();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | RuntimeException ignored) {
                activity.runOnUiThread(() -> {
                    if (!isAdded() || generation != loadGeneration) {
                        return;
                    }
                    snapshot = new ScheduleSnapshot(null, null, null);
                    renderSchedules();
                });
            }
        }, "timetable-load-result").start();
    }

    private void renderSchedules() {
        if (scheduleAdapter == null) {
            return;
        }
        LocalDate periodStart;
        LocalDate periodEnd;
        if (viewMode == MODE_DAY) {
            periodStart = selectedDate;
            periodEnd = selectedDate;
        } else if (viewMode == MODE_WEEK) {
            periodStart = WeekSettings.weekStart(selectedDate);
            periodEnd = periodStart.plusDays(6);
        } else {
            periodStart = selectedDate.withDayOfMonth(1);
            periodEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth());
        }

        // Cancelled sessions stay visible (struck through); they are not counted.
        List<ClassOccurrence> occurrences = ScheduleResolver.resolve(
                snapshot, periodStart.toEpochDay(), periodEnd.toEpochDay(), true);
        List<ScheduleItem> visibleSchedules = new ArrayList<>();
        int activeCount = 0;
        for (ClassOccurrence occurrence : occurrences) {
            visibleSchedules.add(toScheduleItem(occurrence));
            if (!occurrence.isCancelled()) {
                activeCount++;
            }
        }
        scheduleAdapter.setShowDayLabel(viewMode != MODE_DAY);
        scheduleAdapter.updateData(visibleSchedules);
        if (viewMode == MODE_DAY) {
            tvClassCountSubtitle.setText(getString(R.string.classes_count_format, activeCount));
        } else if (viewMode == MODE_WEEK) {
            String week = tvSelectedDate.getText().toString();
            tvClassCountSubtitle.setText(getString(
                    R.string.schedule_week_count_format, week, activeCount));
        } else {
            String month = tvSelectedDate.getText().toString();
            tvClassCountSubtitle.setText(getString(
                    R.string.schedule_month_count_format, month, activeCount));
        }
        tvEmptySchedule.setVisibility(visibleSchedules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private ScheduleItem toScheduleItem(ClassOccurrence occurrence) {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        LocalDate date = LocalDate.ofEpochDay(occurrence.epochDay);
        String semester = isBlank(occurrence.semesterAcademicYear)
                ? safe(occurrence.semesterName)
                : getString(R.string.semester_label_format,
                occurrence.semesterName, occurrence.semesterAcademicYear);
        String dateText = date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT));
        String dayLabel = days[date.getDayOfWeek().getValue() - 1];
        if (viewMode != MODE_DAY) {
            // Week and month lists mix several dates, so each row shows its date.
            dayLabel = dayLabel + " " + date.format(DateTimeFormatter.ofPattern("dd/MM", Locale.ROOT));
        }
        ScheduleItem item = new ScheduleItem(
                String.valueOf(occurrence.scheduleId),
                safe(occurrence.classCode),
                safe(occurrence.subjectName),
                formatCardTime(occurrence.startMinute),
                formatCardTime(occurrence.endMinute),
                safe(occurrence.room),
                safe(occurrence.lecturer),
                dayLabel,
                safe(occurrence.subjectColorKey),
                semester,
                safe(occurrence.note),
                dateText);
        item.setStatus(occurrence.status);
        item.setChangeNote(occurrence.changeNote);
        item.setEpochDay(occurrence.epochDay);
        item.setExceptionId(occurrence.exceptionId);
        return item;
    }

    private void resetToggleButtonsStyle() {
        int transparent = ContextCompat.getColor(requireContext(), R.color.transparent);
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        btnModeDay.setBackgroundTintList(ColorStateList.valueOf(transparent));
        btnModeDay.setTextColor(textColor);
        btnModeWeek.setBackgroundTintList(ColorStateList.valueOf(transparent));
        btnModeWeek.setTextColor(textColor);
        btnModeMonth.setBackgroundTintList(ColorStateList.valueOf(transparent));
        btnModeMonth.setTextColor(textColor);
    }

    private void resetInactiveButtonsStyle() {
        int transparent = ContextCompat.getColor(requireContext(), R.color.transparent);
        int textColor = ContextCompat.getColor(requireContext(), R.color.text_secondary);
        if (viewMode != MODE_DAY) {
            btnModeDay.setBackgroundTintList(ColorStateList.valueOf(transparent));
            btnModeDay.setTextColor(textColor);
        }
        if (viewMode != MODE_WEEK) {
            btnModeWeek.setBackgroundTintList(ColorStateList.valueOf(transparent));
            btnModeWeek.setTextColor(textColor);
        }
        if (viewMode != MODE_MONTH) {
            btnModeMonth.setBackgroundTintList(ColorStateList.valueOf(transparent));
            btnModeMonth.setTextColor(textColor);
        }
    }

    private void styleActiveButton(MaterialButton button) {
        int surfaceColor = ContextCompat.getColor(requireContext(), R.color.surface_light);
        int primaryColor = ContextCompat.getColor(requireContext(), R.color.primary);
        button.setBackgroundTintList(ColorStateList.valueOf(surfaceColor));
        button.setTextColor(primaryColor);
    }

    private static String formatCardTime(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}