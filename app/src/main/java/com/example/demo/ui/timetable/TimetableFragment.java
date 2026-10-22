package com.example.demo.ui.timetable;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.adapter.ScheduleAdapter;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
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
    private List<ScheduleDetails> allSchedules = new ArrayList<>();
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

        scheduleAdapter = new ScheduleAdapter(new ArrayList<>(), item -> {
            if (getActivity() instanceof MainActivity) {
                try {
                    ((MainActivity) getActivity()).openScheduleDetail(Long.parseLong(item.getId()));
                } catch (NumberFormatException ignored) {
                    // Rows are generated only from positive Room schedule IDs.
                }
            }
        });
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
        updateDateDisplay();
        resetToggleButtonsStyle();
        styleActiveButton(btnModeDay);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null && rvTimetableClasses != null) {
            loadScheduleDetails();
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
            LocalDate weekStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
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

    private void loadScheduleDetails() {
        int generation = ++loadGeneration;
        MainActivity activity = getActivity() instanceof MainActivity ? (MainActivity) getActivity() : null;
        if (activity == null) {
            return;
        }
        Future<List<ScheduleDetails>> load = repository.getAllScheduleDetails();
        new Thread(() -> {
            try {
                List<ScheduleDetails> result = load.get();
                activity.runOnUiThread(() -> {
                    if (!isAdded()) {
                        return;
                    }
                    if (generation != loadGeneration) {
                        return;
                    }
                    allSchedules = result == null ? new ArrayList<>() : result;
                    renderSchedules();
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException ignored) {
                activity.runOnUiThread(() -> {
                    if (!isAdded() || generation != loadGeneration) {
                        return;
                    }
                    allSchedules = new ArrayList<>();
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
            periodStart = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            periodEnd = periodStart.plusDays(6);
        } else {
            periodStart = selectedDate.withDayOfMonth(1);
            periodEnd = selectedDate.withDayOfMonth(selectedDate.lengthOfMonth());
        }

        List<ScheduleItem> visibleSchedules = new ArrayList<>();
        for (ScheduleDetails schedule : allSchedules) {
            if (hasOccurrence(schedule, periodStart, periodEnd)) {
                visibleSchedules.add(toScheduleItem(schedule));
            }
        }
        scheduleAdapter.setShowDayLabel(viewMode != MODE_DAY);
        scheduleAdapter.updateData(visibleSchedules);
        if (viewMode == MODE_DAY) {
            tvClassCountSubtitle.setText(getString(R.string.classes_count_format, visibleSchedules.size()));
        } else if (viewMode == MODE_WEEK) {
            String week = tvSelectedDate.getText().toString();
            tvClassCountSubtitle.setText(getString(
                    R.string.schedule_week_count_format, week, visibleSchedules.size()));
        } else {
            String month = tvSelectedDate.getText().toString();
            tvClassCountSubtitle.setText(getString(
                    R.string.schedule_month_count_format, month, visibleSchedules.size()));
        }
        tvEmptySchedule.setVisibility(visibleSchedules.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private static boolean hasOccurrence(ScheduleDetails schedule, LocalDate periodStart, LocalDate periodEnd) {
        LocalDate first = periodStart.isAfter(LocalDate.ofEpochDay(schedule.validFromEpochDay))
                ? periodStart : LocalDate.ofEpochDay(schedule.validFromEpochDay);
        LocalDate last = periodEnd.isBefore(LocalDate.ofEpochDay(schedule.validToEpochDay))
                ? periodEnd : LocalDate.ofEpochDay(schedule.validToEpochDay);
        if (first.isAfter(last)) {
            return false;
        }
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            if (date.getDayOfWeek().getValue() == schedule.dayOfWeek) {
                return true;
            }
        }
        return false;
    }

    private ScheduleItem toScheduleItem(ScheduleDetails details) {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        String semester = details.semesterAcademicYear == null || details.semesterAcademicYear.trim().isEmpty()
                ? safe(details.semesterName)
                : getString(R.string.semester_label_format, details.semesterName, details.semesterAcademicYear);
        String appliedPeriod = LocalDate.ofEpochDay(details.validFromEpochDay)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
                + " – " + LocalDate.ofEpochDay(details.validToEpochDay)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT));
        return new ScheduleItem(
                String.valueOf(details.scheduleId),
                safe(details.classCode),
                safe(details.subjectName),
                formatCardTime(details.startMinute),
                formatCardTime(details.endMinute),
                safe(details.room),
                safe(details.lecturer),
                days[details.dayOfWeek - 1],
                safe(details.subjectColorKey),
                semester,
                safe(details.note),
                appliedPeriod
        );
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
