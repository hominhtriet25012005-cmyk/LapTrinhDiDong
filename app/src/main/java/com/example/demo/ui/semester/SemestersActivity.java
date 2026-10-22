package com.example.demo.ui.semester;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.R;
import com.example.demo.adapter.SemesterAdapter;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.model.SemesterItem;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class SemestersActivity extends AppCompatActivity {
    private static final DateTimeFormatter DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private SemesterAdapter adapter;
    private AppRepository repository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_semesters);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        RecyclerView rvSemesters = findViewById(R.id.rvSemesters);
        TextView tvEmptySemesters = findViewById(R.id.tvEmptySemesters);
        rvSemesters.setLayoutManager(new LinearLayoutManager(this));
        adapter = new SemesterAdapter(new ArrayList<>(), this::openSemesterForm);
        rvSemesters.setAdapter(adapter);

        repository = AppRepository.getInstance(getApplicationContext());
        repository.observeSemesterSummaries().observe(this, summaries -> {
            List<SemesterItem> items = new ArrayList<>();
            for (SemesterSummary summary : summaries) {
                items.add(toSemesterItem(summary));
            }
            adapter.updateData(items);
            boolean isEmpty = items.isEmpty();
            rvSemesters.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
            tvEmptySemesters.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        });

        ExtendedFloatingActionButton fabAdd = findViewById(R.id.fabAddSemester);
        fabAdd.setOnClickListener(v -> startActivity(new Intent(this, SemesterFormActivity.class)));
    }

    private void openSemesterForm(SemesterItem item) {
        Intent intent = new Intent(this, SemesterFormActivity.class);
        intent.putExtra(SemesterFormActivity.EXTRA_SEMESTER_ID, Long.parseLong(item.getId()));
        startActivity(intent);
    }

    private static SemesterItem toSemesterItem(SemesterSummary summary) {
        SemesterEntity semester = summary.semester;
        String startDate = formatDate(semester.startDateEpochDay);
        String endDate = formatDate(semester.endDateEpochDay);
        return new SemesterItem(
                String.valueOf(semester.id),
                semester.name,
                semester.academicYear == null ? "—" : semester.academicYear,
                startDate,
                endDate,
                summary.subjectCount,
                semester.isCurrent,
                semester.note
        );
    }

    private static String formatDate(Long epochDay) {
        return epochDay == null ? null : LocalDate.ofEpochDay(epochDay).format(DISPLAY_DATE_FORMAT);
    }
}
