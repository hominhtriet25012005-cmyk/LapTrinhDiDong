package com.example.demo.ui.home;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.adapter.QuickActionAdapter;
import com.example.demo.adapter.ScheduleAdapter;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.validation.HomeDashboardCalculator;
import com.example.demo.model.QuickActionItem;
import com.example.demo.model.ScheduleItem;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class HomeFragment extends Fragment {
    private static final long ONE_MINUTE_MILLIS = 60_000L;

    private RecyclerView rvTodayClasses;
    private ScheduleAdapter scheduleAdapter;
    private AppRepository repository;
    private LiveData<List<SubjectEntity>> currentSubjectsSource;
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockTick = this::onClockTick;
    private List<ScheduleDetails> cachedScheduleDetails;
    private LocalDate cachedScheduleDate;
    private ScheduleDetails nextClass;
    private int loadGeneration;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.getInstance(requireContext());
        rvTodayClasses = view.findViewById(R.id.rvTodayClasses);

        setupTodayClasses();
        setupQuickActions(view.findViewById(R.id.rvQuickActions));
        view.findViewById(R.id.btnSeeAllSchedule).setOnClickListener(v -> withMainActivity(
                activity -> activity.navigateToTab(R.id.menu_timetable)));
        view.findViewById(R.id.btnHeroSeeDetail).setOnClickListener(v -> openNextClass());
        view.findViewById(R.id.cardNextClass).setOnClickListener(v -> openNextClass());
        view.findViewById(R.id.btnAvatar).setOnClickListener(v -> withMainActivity(
                activity -> activity.navigateToTab(R.id.menu_settings)));
        view.findViewById(R.id.btnSemesterChip).setOnClickListener(v -> withMainActivity(
                MainActivity::openSemesters));

        TextView tvCurrentSemester = view.findViewById(R.id.tvCurrentSemester);
        TextView tvStatSubjectsCount = view.findViewById(R.id.tvStatSubjectsCount);
        repository.observeCurrentSemester().observe(getViewLifecycleOwner(), semester -> {
            if (currentSubjectsSource != null) {
                currentSubjectsSource.removeObservers(getViewLifecycleOwner());
                currentSubjectsSource = null;
            }
            if (semester == null) {
                tvCurrentSemester.setText(R.string.home_no_current_semester);
                tvStatSubjectsCount.setText("0");
                return;
            }
            tvCurrentSemester.setText(formatSemester(semester));
            tvStatSubjectsCount.setText(R.string.home_stat_pending);
            currentSubjectsSource = repository.observeSubjects(semester.id);
            currentSubjectsSource.observe(getViewLifecycleOwner(), subjects ->
                    tvStatSubjectsCount.setText(String.valueOf(subjects == null ? 0 : subjects.size())));
        });

        updateDateLabel();
        showLoadingState();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (repository != null && rvTodayClasses != null && isVisible()) {
            refreshDashboard();
            scheduleNextClockTick();
        }
    }

    @Override
    public void onPause() {
        clockHandler.removeCallbacks(clockTick);
        super.onPause();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (hidden) {
            clockHandler.removeCallbacks(clockTick);
        } else if (repository != null && rvTodayClasses != null) {
            refreshDashboard();
            scheduleNextClockTick();
        }
    }

    @Override
    public void onDestroyView() {
        loadGeneration++;
        clockHandler.removeCallbacks(clockTick);
        rvTodayClasses = null;
        scheduleAdapter = null;
        cachedScheduleDetails = null;
        cachedScheduleDate = null;
        super.onDestroyView();
    }

    private void setupTodayClasses() {
        scheduleAdapter = new ScheduleAdapter(new ArrayList<>(), item -> {
            try {
                long scheduleId = Long.parseLong(item.getId());
                withMainActivity(activity -> activity.openScheduleDetail(scheduleId));
            } catch (NumberFormatException ignored) {
                // Every row is created from a persisted Room schedule ID.
            }
        });
        scheduleAdapter.setShowDayLabel(false);
        rvTodayClasses.setLayoutManager(new LinearLayoutManager(getContext()));
        rvTodayClasses.setAdapter(scheduleAdapter);
    }

    private void setupQuickActions(RecyclerView rvQuickActions) {
        List<QuickActionItem> actions = new ArrayList<>();
        actions.add(new QuickActionItem(getString(R.string.quick_add_schedule), R.drawable.ic_plus,
                R.drawable.bg_round_icon_blue, R.color.primary, "add_schedule"));
        actions.add(new QuickActionItem(getString(R.string.quick_subjects), R.drawable.ic_nav_book,
                R.drawable.bg_round_icon_green, R.color.tag_green_text, "subjects"));
        actions.add(new QuickActionItem(getString(R.string.quick_semesters), R.drawable.ic_graduation_cap,
                R.drawable.bg_round_icon_amber, R.color.tag_amber_text, "semesters"));
        actions.add(new QuickActionItem(getString(R.string.quick_stats), R.drawable.ic_chart,
                R.drawable.bg_round_icon_violet, R.color.tag_violet_text, "stats"));

        QuickActionAdapter quickActionAdapter = new QuickActionAdapter(actions, action ->
                withMainActivity(activity -> {
                    if ("add_schedule".equals(action.getActionKey())) {
                        activity.openAddSchedule();
                    } else if ("subjects".equals(action.getActionKey())) {
                        activity.navigateToTab(R.id.menu_subjects);
                    } else if ("semesters".equals(action.getActionKey())) {
                        activity.openSemesters();
                    } else if ("stats".equals(action.getActionKey())) {
                        activity.openStats();
                    }
                }));
        rvQuickActions.setLayoutManager(new GridLayoutManager(getContext(), 4));
        rvQuickActions.setAdapter(quickActionAdapter);
    }

    private void refreshDashboard() {
        if (!isAdded() || getView() == null) {
            return;
        }
        updateDateLabel();
        showLoadingState();
        int generation = ++loadGeneration;
        LocalDate today = LocalDate.now();
        cachedScheduleDetails = null;
        cachedScheduleDate = null;
        LocalTime currentTime = LocalTime.now();
        int currentMinute = currentTime.getHour() * 60 + currentTime.getMinute();
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        Future<List<ScheduleDetails>> load = repository.getAllScheduleDetails();
        new Thread(() -> {
            try {
                List<ScheduleDetails> schedules = load.get();
                List<ScheduleDetails> loadedSchedules = schedules == null
                        ? new ArrayList<>() : new ArrayList<>(schedules);
                HomeDashboardCalculator.DashboardSummary summary =
                        HomeDashboardCalculator.calculate(loadedSchedules, today, currentMinute);
                activity.runOnUiThread(() -> {
                    if (!isAdded() || getView() == null || generation != loadGeneration) {
                        return;
                    }
                    cachedScheduleDetails = loadedSchedules;
                    cachedScheduleDate = today;
                    renderDashboard(summary);
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                showLoadError(activity, generation);
            } catch (ExecutionException | RuntimeException exception) {
                showLoadError(activity, generation);
            }
        }, "home-dashboard-load").start();
    }

    private void showLoadError(Activity activity, int generation) {
        activity.runOnUiThread(() -> {
            if (!isAdded() || getView() == null || generation != loadGeneration) {
                return;
            }
            TextView empty = getView().findViewById(R.id.tvTodayEmpty);
            empty.setText(R.string.home_schedule_load_error);
            empty.setVisibility(View.VISIBLE);
            rvTodayClasses.setVisibility(View.GONE);
            setText(R.id.tvStatClassesCount, R.string.home_stat_pending);
            setText(R.id.tvStatHoursCount, R.string.home_stat_pending);
            setText(R.id.tvWeekSummary, R.string.home_schedule_load_error);
            setText(R.id.tvTodayFreeTime, R.string.home_schedule_load_error);
            nextClass = null;
            cachedScheduleDetails = null;
            cachedScheduleDate = null;
            renderNextClass(null, false, R.string.home_schedule_load_error);
        });
    }

    private void showLoadingState() {
        if (getView() == null) {
            return;
        }
        TextView empty = getView().findViewById(R.id.tvTodayEmpty);
        empty.setText(R.string.home_loading_schedule);
        empty.setVisibility(View.VISIBLE);
        rvTodayClasses.setVisibility(View.GONE);
        setText(R.id.tvWeekSummary, R.string.home_loading_schedule);
        setText(R.id.tvTodayFreeTime, R.string.home_loading_schedule);
        nextClass = null;
        renderNextClass(null, false, R.string.home_loading_schedule);
    }

    private void renderDashboard(HomeDashboardCalculator.DashboardSummary summary) {
        nextClass = summary.nextClass;
        updateDateLabel();
        setText(R.id.tvStatClassesCount, String.valueOf(summary.todaySchedules.size()));
        setText(R.id.tvStatHoursCount, formatDuration(summary.todayMinutes));
        setText(R.id.tvWeekSummary, getString(R.string.home_week_summary,
                summary.weekSchedules.size(), formatDuration(summary.weekMinutes)));

        List<ScheduleItem> todayItems = new ArrayList<>();
        for (ScheduleDetails details : summary.todaySchedules) {
            todayItems.add(toScheduleItem(details));
        }
        scheduleAdapter.updateData(todayItems);
        rvTodayClasses.setVisibility(todayItems.isEmpty() ? View.GONE : View.VISIBLE);
        TextView empty = getView().findViewById(R.id.tvTodayEmpty);
        empty.setText(R.string.home_today_empty);
        empty.setVisibility(todayItems.isEmpty() ? View.VISIBLE : View.GONE);

        int emptyHeroMessage = summary.todaySchedules.isEmpty()
                ? R.string.home_no_class_today : R.string.home_all_classes_finished;
        renderNextClass(summary.nextClass, summary.nextClassOngoing, emptyHeroMessage);
        renderFreeTime(summary);
    }

    private void renderNextClass(ScheduleDetails details, boolean ongoing, int emptyMessage) {
        View root = getView();
        if (root == null) {
            return;
        }
        View card = root.findViewById(R.id.cardNextClass);
        TextView label = root.findViewById(R.id.tvNextClassLabel);
        TextView subject = root.findViewById(R.id.tvNextSubjectName);
        TextView badge = root.findViewById(R.id.tvLiveRemaining);
        View metadata = root.findViewById(R.id.layoutNextClassMeta);
        View footer = root.findViewById(R.id.layoutNextClassFooter);
        View divider = root.findViewById(R.id.dividerNextClass);
        View detail = root.findViewById(R.id.btnHeroSeeDetail);

        if (details == null) {
            label.setText(R.string.home_today_schedule_label);
            subject.setText(emptyMessage);
            badge.setVisibility(View.GONE);
            metadata.setVisibility(View.GONE);
            divider.setVisibility(View.GONE);
            footer.setVisibility(View.GONE);
            detail.setVisibility(View.GONE);
            card.setClickable(false);
            card.setFocusable(false);
            return;
        }

        label.setText(ongoing ? R.string.home_current_class_label : R.string.next_class_label);
        subject.setText(safe(details.subjectName));
        LocalTime currentTime = LocalTime.now();
        int startMinute = currentTime.getHour() * 60 + currentTime.getMinute();
        int minutesUntilStart = details.startMinute - startMinute;
        if (ongoing) {
            badge.setText(getString(R.string.home_remaining_time,
                    formatDuration(Math.max(0, details.endMinute - startMinute))));
        } else if (minutesUntilStart < 60) {
            badge.setText(getString(R.string.home_starts_in_minutes, Math.max(0, minutesUntilStart)));
        } else {
            badge.setText(getString(R.string.home_starts_in_hours,
                    minutesUntilStart / 60, minutesUntilStart % 60));
        }
        badge.setVisibility(View.VISIBLE);
        ((TextView) root.findViewById(R.id.tvNextTime)).setText(
                formatClock(details.startMinute) + " – " + formatClock(details.endMinute));
        ((TextView) root.findViewById(R.id.tvNextRoom)).setText(
                isBlank(details.room) ? getString(R.string.home_room_not_set) : details.room);
        ((TextView) root.findViewById(R.id.tvNextLecturer)).setText(
                isBlank(details.lecturer) ? getString(R.string.home_lecturer_not_set) : details.lecturer);
        metadata.setVisibility(View.VISIBLE);
        divider.setVisibility(View.VISIBLE);
        footer.setVisibility(View.VISIBLE);
        detail.setVisibility(View.VISIBLE);
        card.setClickable(true);
        card.setFocusable(true);
    }

    private void renderFreeTime(HomeDashboardCalculator.DashboardSummary summary) {
        TextView value = getView().findViewById(R.id.tvTodayFreeTime);
        if (summary.todaySchedules.size() < 2) {
            value.setText(R.string.home_free_time_not_enough);
            return;
        }
        if (summary.freeTimeGaps.isEmpty()) {
            value.setText(R.string.home_free_time_none);
            return;
        }
        StringBuilder text = new StringBuilder();
        for (HomeDashboardCalculator.FreeTimeGap gap : summary.freeTimeGaps) {
            if (text.length() > 0) {
                text.append(getString(R.string.home_free_time_separator));
            }
            text.append(getString(R.string.home_free_time_range,
                    formatClock(gap.startMinute), formatClock(gap.endMinute),
                    formatDuration(gap.durationMinutes())));
        }
        value.setText(text.toString());
    }

    private void openNextClass() {
        if (nextClass != null) {
            long id = nextClass.scheduleId;
            withMainActivity(activity -> activity.openScheduleDetail(id));
        }
    }

    private void onClockTick() {
        if (!isAdded() || getView() == null || !isVisible()) {
            return;
        }
        LocalDate today = LocalDate.now();
        if (cachedScheduleDetails == null || !today.equals(cachedScheduleDate)) {
            refreshDashboard();
        } else {
            LocalTime currentTime = LocalTime.now();
            int currentMinute = currentTime.getHour() * 60 + currentTime.getMinute();
            HomeDashboardCalculator.DashboardSummary summary =
                    HomeDashboardCalculator.calculate(cachedScheduleDetails, today, currentMinute);
            nextClass = summary.nextClass;
            int emptyMessage = summary.todaySchedules.isEmpty()
                    ? R.string.home_no_class_today : R.string.home_all_classes_finished;
            renderNextClass(summary.nextClass, summary.nextClassOngoing, emptyMessage);
        }
        scheduleNextClockTick();
    }

    private void scheduleNextClockTick() {
        clockHandler.removeCallbacks(clockTick);
        long delay = ONE_MINUTE_MILLIS - (System.currentTimeMillis() % ONE_MINUTE_MILLIS);
        clockHandler.postDelayed(clockTick, delay);
    }

    private void updateDateLabel() {
        if (getView() == null) {
            return;
        }
        Locale vietnamese = new Locale("vi", "VN");
        String date = LocalDate.now().format(
                DateTimeFormatter.ofPattern("EEEE, d 'tháng' M 'năm' yyyy", vietnamese));
        setText(R.id.tvDate, date);
    }

    private ScheduleItem toScheduleItem(ScheduleDetails details) {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        String semester = isBlank(details.semesterAcademicYear)
                ? safe(details.semesterName)
                : getString(R.string.semester_label_format,
                        details.semesterName, details.semesterAcademicYear);
        String appliedPeriod = LocalDate.ofEpochDay(details.validFromEpochDay)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT))
                + " – " + LocalDate.ofEpochDay(details.validToEpochDay)
                .format(DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT));
        return new ScheduleItem(String.valueOf(details.scheduleId), safe(details.classCode),
                safe(details.subjectName), formatClock(details.startMinute), formatClock(details.endMinute),
                safe(details.room), safe(details.lecturer), days[details.dayOfWeek - 1],
                safe(details.subjectColorKey), semester, safe(details.note), appliedPeriod);
    }

    private String formatSemester(SemesterEntity semester) {
        if (isBlank(semester.academicYear)) {
            return safe(semester.name);
        }
        return getString(R.string.semester_label_format, semester.name, semester.academicYear);
    }

    private String formatDuration(int minutes) {
        int hours = Math.max(0, minutes) / 60;
        int remainingMinutes = Math.max(0, minutes) % 60;
        if (hours == 0) {
            return getString(R.string.home_duration_minutes, remainingMinutes);
        }
        if (remainingMinutes == 0) {
            return getString(R.string.home_duration_hours, hours);
        }
        return getString(R.string.home_duration_hours_minutes, hours, remainingMinutes);
    }

    private static String formatClock(int minuteOfDay) {
        return String.format(Locale.ROOT, "%02d:%02d", minuteOfDay / 60, minuteOfDay % 60);
    }

    private void setText(int viewId, String text) {
        TextView textView = getView() == null ? null : getView().findViewById(viewId);
        if (textView != null) {
            textView.setText(text);
        }
    }

    private void setText(int viewId, int stringId) {
        TextView textView = getView() == null ? null : getView().findViewById(viewId);
        if (textView != null) {
            textView.setText(stringId);
        }
    }

    private void withMainActivity(java.util.function.Consumer<MainActivity> action) {
        if (getActivity() instanceof MainActivity) {
            action.accept((MainActivity) getActivity());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
