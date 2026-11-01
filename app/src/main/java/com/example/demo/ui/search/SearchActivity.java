package com.example.demo.ui.search;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.adapter.ScheduleAdapter;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.ScheduleDetails;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.data.schedule.ClassOccurrence;
import com.example.demo.data.schedule.ScheduleResolver;
import com.example.demo.data.schedule.ScheduleSnapshot;
import com.example.demo.data.schedule.SessionSearch;
import com.example.demo.model.ScheduleItem;
import com.example.demo.ui.detail.ScheduleDetailActivity;
import com.example.demo.ui.occurrence.OccurrenceActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class SearchActivity extends AppCompatActivity {

    private static final DateTimeFormatter DISPLAY_DATE_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("vi", "VN"));

    private EditText etSearchQuery;
    private ImageView btnClearSearch;
    private TextView btnClearAllFilters;
    private AutoCompleteTextView actvFilterSemester;
    private ChipGroup chipGroupWeekdays;
    private EditText etFilterRoom;
    private MaterialButton btnApplyFilter;
    private MaterialButton btnFilterFromDate;
    private MaterialButton btnFilterToDate;
    private TextView tvResultCount;
    private RecyclerView rvSearchResults;
    private LinearLayout layoutSearchEmpty;
    private MaterialButton btnResetFilter;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final List<SemesterEntity> semesters = new ArrayList<>();
    private final List<Long> semesterOptionIds = new ArrayList<>();
    private AppRepository repository;
    private ScheduleAdapter adapter;
    private LiveData<List<ScheduleDetails>> activeSearch;
    private long selectedSemesterId;
    private Long fromEpochDay;
    private Long toEpochDay;
    private boolean occurrenceMode;
    private boolean resumedOnce;
    private int searchGeneration;
    private boolean suppressSearchUpdates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        etSearchQuery = findViewById(R.id.etSearchQuery);
        btnClearSearch = findViewById(R.id.btnClearSearch);
        btnClearAllFilters = findViewById(R.id.btnClearAllFilters);
        actvFilterSemester = findViewById(R.id.actvFilterSemester);
        chipGroupWeekdays = findViewById(R.id.chipGroupWeekdays);
        etFilterRoom = findViewById(R.id.etFilterRoom);
        btnApplyFilter = findViewById(R.id.btnApplyFilter);
        btnFilterFromDate = findViewById(R.id.btnFilterFromDate);
        btnFilterToDate = findViewById(R.id.btnFilterToDate);
        tvResultCount = findViewById(R.id.tvResultCount);
        rvSearchResults = findViewById(R.id.rvSearchResults);
        layoutSearchEmpty = findViewById(R.id.layoutSearchEmpty);
        btnResetFilter = findViewById(R.id.btnResetFilter);

        repository = AppRepository.getInstance(getApplicationContext());
        setupList();
        setupListeners();
        setupSemesterDropdown();
        renderDateButtons();

        repository.observeSemesters().observe(this, this::bindSemesters);
        runSearch();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Coming back from the session screen: a session may have been cancelled, moved or restored.
        if (resumedOnce && occurrenceMode) {
            runSearch();
        }
        resumedOnce = true;
    }

    private void setupSemesterDropdown() {
        actvFilterSemester.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < semesterOptionIds.size()) {
                selectedSemesterId = semesterOptionIds.get(position);
                runSearch();
            }
        });
    }

    private void bindSemesters(List<SemesterEntity> values) {
        semesters.clear();
        if (values != null) {
            semesters.addAll(values);
        }

        List<String> labels = new ArrayList<>();
        semesterOptionIds.clear();
        labels.add(getString(R.string.filter_all_semesters));
        semesterOptionIds.add(0L);

        boolean selectedSemesterExists = selectedSemesterId == 0L;
        int selectedPosition = 0;
        for (SemesterEntity semester : semesters) {
            labels.add(formatSemester(semester));
            semesterOptionIds.add(semester.id);
            if (semester.id == selectedSemesterId) {
                selectedSemesterExists = true;
                selectedPosition = labels.size() - 1;
            }
        }
        if (!selectedSemesterExists) {
            selectedSemesterId = 0L;
            selectedPosition = 0;
        }

        ArrayAdapter<String> semAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_dropdown_item_1line, labels);
        actvFilterSemester.setAdapter(semAdapter);
        actvFilterSemester.setText(labels.get(selectedPosition), false);
        runSearch();
    }

    private void setupList() {
        rvSearchResults.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ScheduleAdapter(new ArrayList<>(), this::onResultClick);
        adapter.setShowDayLabel(true);
        rvSearchResults.setAdapter(adapter);
    }

    private void onResultClick(ScheduleItem item) {
        long scheduleId;
        try {
            scheduleId = Long.parseLong(item.getId());
        } catch (NumberFormatException exception) {
            return; // Items are built from Room IDs; ignore malformed values defensively.
        }
        if (occurrenceMode) {
            Intent intent = new Intent(this, OccurrenceActivity.class);
            intent.putExtra(OccurrenceActivity.EXTRA_SCHEDULE_ID, scheduleId);
            intent.putExtra(OccurrenceActivity.EXTRA_EXCEPTION_ID, item.getExceptionId());
            intent.putExtra(OccurrenceActivity.EXTRA_EPOCH_DAY, item.getEpochDay());
            startActivity(intent);
        } else {
            Intent intent = new Intent(this, ScheduleDetailActivity.class);
            intent.putExtra(ScheduleDetailActivity.EXTRA_SCHEDULE_ID, scheduleId);
            startActivity(intent);
        }
    }

    private void setupListeners() {
        btnClearSearch.setOnClickListener(v -> etSearchQuery.setText(""));
        btnClearAllFilters.setOnClickListener(v -> clearAllFilters());
        btnResetFilter.setOnClickListener(v -> clearAllFilters());
        btnApplyFilter.setOnClickListener(v -> runSearch());

        btnFilterFromDate.setOnClickListener(v -> pickDate(true));
        btnFilterToDate.setOnClickListener(v -> pickDate(false));
        // Long-press a date button to clear just that date.
        btnFilterFromDate.setOnLongClickListener(v -> {
            fromEpochDay = null;
            renderDateButtons();
            runSearch();
            return true;
        });
        btnFilterToDate.setOnLongClickListener(v -> {
            toEpochDay = null;
            renderDateButtons();
            runSearch();
            return true;
        });

        TextWatcher searchWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (!suppressSearchUpdates) {
                    runSearch();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
        etSearchQuery.addTextChangedListener(searchWatcher);
        etFilterRoom.addTextChangedListener(searchWatcher);
        etSearchQuery.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                runSearch();
                return true;
            }
            return false;
        });

        chipGroupWeekdays.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (!suppressSearchUpdates) {
                runSearch();
            }
        });
    }

    private void pickDate(boolean isFrom) {
        Long current = isFrom ? fromEpochDay : toEpochDay;
        long selection = current != null
                ? current * 86_400_000L : MaterialDatePicker.todayInUtcMilliseconds();
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isFrom ? R.string.search_from_date : R.string.search_to_date)
                .setSelection(selection)
                .build();
        picker.addOnPositiveButtonClickListener(millis -> {
            long epochDay = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay();
            if (isFrom) {
                fromEpochDay = epochDay;
            } else {
                toEpochDay = epochDay;
            }
            renderDateButtons();
            runSearch();
        });
        picker.show(getSupportFragmentManager(), isFrom ? "search_from" : "search_to");
    }

    private void renderDateButtons() {
        btnFilterFromDate.setText(fromEpochDay == null ? getString(R.string.search_from_date)
                : getString(R.string.search_from_date_value, formatDate(fromEpochDay)));
        btnFilterToDate.setText(toEpochDay == null ? getString(R.string.search_to_date)
                : getString(R.string.search_to_date_value, formatDate(toEpochDay)));
    }

    private void clearAllFilters() {
        suppressSearchUpdates = true;
        etSearchQuery.setText("");
        etFilterRoom.setText("");
        selectedSemesterId = 0L;
        fromEpochDay = null;
        toEpochDay = null;
        renderDateButtons();
        if (!semesterOptionIds.isEmpty()) {
            actvFilterSemester.setText(getString(R.string.filter_all_semesters), false);
        }
        chipGroupWeekdays.check(R.id.chipAllDays);
        suppressSearchUpdates = false;
        runSearch();
    }

    private void runSearch() {
        if (repository == null || adapter == null) {
            return;
        }
        if (activeSearch != null) {
            activeSearch.removeObservers(this);
            activeSearch = null;
        }
        final int generation = ++searchGeneration;
        final String query = etSearchQuery.getText() == null
                ? "" : etSearchQuery.getText().toString().trim();
        final String roomQuery = etFilterRoom.getText() == null
                ? "" : etFilterRoom.getText().toString().trim();

        if (fromEpochDay == null && toEpochDay == null) {
            occurrenceMode = false;
            activeSearch = repository.searchScheduleDetails(
                    query, selectedSemesterId, selectedDayOfWeek(), roomQuery);
            activeSearch.observe(this, this::renderResults);
            return;
        }

        occurrenceMode = true;
        // Only "from" (or only "to") means one specific day; both mean an inclusive range.
        final long from = fromEpochDay != null ? fromEpochDay : toEpochDay;
        final long to = fromEpochDay != null && toEpochDay != null ? toEpochDay : from;
        if (to < from) {
            showMessage(getString(R.string.search_date_invalid));
            return;
        }
        if (to - from + 1 > SessionSearch.MAX_RANGE_DAYS) {
            showMessage(getString(R.string.search_date_too_long, SessionSearch.MAX_RANGE_DAYS));
            return;
        }
        final long semesterId = selectedSemesterId;
        final int dayOfWeek = selectedDayOfWeek();

        new Thread(() -> {
            try {
                ScheduleSnapshot snapshot = repository.getScheduleSnapshot().get();
                List<ClassOccurrence> sessions = SessionSearch.filter(
                        ScheduleResolver.resolve(snapshot, from, to, true),
                        query, semesterId, dayOfWeek, roomQuery);
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    renderSessions(sessions);
                });
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | RuntimeException exception) {
                mainHandler.post(() -> {
                    if (isFinishing() || isDestroyed() || generation != searchGeneration) return;
                    Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
                });
            }
        }, "search-sessions").start();
    }

    private void showMessage(String message) {
        adapter.updateData(new ArrayList<>());
        tvResultCount.setText(message);
        rvSearchResults.setVisibility(View.GONE);
        layoutSearchEmpty.setVisibility(View.GONE);
    }

    private int selectedDayOfWeek() {
        int checkedId = chipGroupWeekdays.getCheckedChipId();
        if (checkedId == R.id.chipT2) return 1;
        if (checkedId == R.id.chipT3) return 2;
        if (checkedId == R.id.chipT4) return 3;
        if (checkedId == R.id.chipT5) return 4;
        if (checkedId == R.id.chipT6) return 5;
        if (checkedId == R.id.chipT7) return 6;
        if (checkedId == R.id.chipCN) return 7;
        return 0;
    }

    private void renderResults(List<ScheduleDetails> details) {
        List<ScheduleItem> items = new ArrayList<>();
        if (details != null) {
            for (ScheduleDetails detail : details) {
                if (detail != null && detail.dayOfWeek >= 1 && detail.dayOfWeek <= 7) {
                    items.add(toScheduleItem(detail));
                }
            }
        }
        showItems(items);
    }

    private void renderSessions(List<ClassOccurrence> sessions) {
        List<ScheduleItem> items = new ArrayList<>();
        for (ClassOccurrence session : sessions) {
            items.add(toScheduleItem(session));
        }
        showItems(items);
    }

    private void showItems(List<ScheduleItem> items) {
        adapter.updateData(items);
        tvResultCount.setText(getString(R.string.search_result_count, items.size()));
        boolean empty = items.isEmpty();
        rvSearchResults.setVisibility(empty ? View.GONE : View.VISIBLE);
        layoutSearchEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private ScheduleItem toScheduleItem(ScheduleDetails details) {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        String room = isBlank(details.room) ? getString(R.string.home_room_not_set) : details.room;
        String lecturer = isBlank(details.lecturer)
                ? getString(R.string.value_not_set) : details.lecturer;
        return new ScheduleItem(
                String.valueOf(details.scheduleId),
                safe(details.subjectCode),
                safe(details.subjectName),
                formatTime(details.startMinute),
                formatTime(details.endMinute),
                room,
                lecturer,
                days[details.dayOfWeek - 1],
                isBlank(details.subjectColorKey) ? "blue" : details.subjectColorKey,
                formatSemester(details.semesterName, details.semesterAcademicYear),
                safe(details.note),
                formatDate(details.validFromEpochDay) + " – " + formatDate(details.validToEpochDay)
        );
    }

    private ScheduleItem toScheduleItem(ClassOccurrence session) {
        String[] days = getResources().getStringArray(R.array.days_of_week);
        LocalDate date = LocalDate.ofEpochDay(session.epochDay);
        String dateText = formatDate(session.epochDay);
        String room = isBlank(session.room) ? getString(R.string.home_room_not_set) : session.room;
        String lecturer = isBlank(session.lecturer)
                ? getString(R.string.value_not_set) : session.lecturer;
        ScheduleItem item = new ScheduleItem(
                String.valueOf(session.scheduleId),
                safe(session.subjectCode),
                safe(session.subjectName),
                formatTime(session.startMinute),
                formatTime(session.endMinute),
                room,
                lecturer,
                days[date.getDayOfWeek().getValue() - 1] + ", " + dateText,
                isBlank(session.subjectColorKey) ? "blue" : session.subjectColorKey,
                formatSemester(session.semesterName, session.semesterAcademicYear),
                safe(session.note),
                dateText
        );
        item.setStatus(session.status);
        item.setChangeNote(session.changeNote);
        item.setEpochDay(session.epochDay);
        item.setExceptionId(session.exceptionId);
        return item;
    }

    private static String formatSemester(SemesterEntity semester) {
        return formatSemester(semester.name, semester.academicYear);
    }

    private static String formatSemester(String name, String academicYear) {
        if (isBlank(academicYear)) {
            return safe(name);
        }
        return String.format(Locale.getDefault(), "%s · %s", safe(name), academicYear);
    }

    private static String formatTime(int minutes) {
        return String.format(Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private static String formatDate(long epochDay) {
        return LocalDate.ofEpochDay(epochDay).format(DISPLAY_DATE_FORMAT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}