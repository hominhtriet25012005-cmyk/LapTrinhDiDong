package com.example.demo.ui.subjects;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.demo.MainActivity;
import com.example.demo.R;
import com.example.demo.adapter.SubjectAdapter;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.AppRepository;
import com.example.demo.model.SubjectItem;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SubjectsFragment extends Fragment {

    private EditText etSearchSubject;
    private Chip chipCurrentSemester;
    private ChipGroup chipGroupSemester;
    private TextView tvSubjectCountSubtitle;
    private RecyclerView rvSubjects;
    private SubjectAdapter subjectAdapter;
    private AppRepository repository;
    private LiveData<List<SubjectEntity>> activeSubjectSource;
    private Observer<List<SubjectEntity>> subjectObserver;
    private long currentSemesterId;
    private boolean showAllSemesters;
    private String query = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_subjects, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = AppRepository.getInstance(requireContext());
        etSearchSubject = view.findViewById(R.id.etSearchSubject);
        chipGroupSemester = view.findViewById(R.id.chipGroupSemester);
        chipCurrentSemester = view.findViewById(R.id.chipCurrentSemester);
        tvSubjectCountSubtitle = view.findViewById(R.id.tvSubjectCountSubtitle);
        rvSubjects = view.findViewById(R.id.rvSubjects);

        subjectAdapter = new SubjectAdapter(new ArrayList<>(), subject -> {
            if (getActivity() instanceof MainActivity) {
                try {
                    ((MainActivity) getActivity()).openSubjectDetail(Long.parseLong(subject.getId()));
                } catch (NumberFormatException ignored) {
                    // Items are always mapped from Room IDs; ignore a malformed transient item safely.
                }
            }
        });
        rvSubjects.setLayoutManager(new LinearLayoutManager(getContext()));
        rvSubjects.setAdapter(subjectAdapter);

        chipGroupSemester.setOnCheckedStateChangeListener((group, checkedIds) -> {
            showAllSemesters = !checkedIds.isEmpty() && checkedIds.get(0) == R.id.chipAllSemesters;
            refreshSubjects();
        });

        etSearchSubject.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s == null ? "" : s.toString().trim();
                refreshSubjects();
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ExtendedFloatingActionButton fabAddSubject = view.findViewById(R.id.fabAddSubject);
        fabAddSubject.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openAddSubject();
            }
        });
        view.findViewById(R.id.btnAdvSearch).setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openSearch();
            }
        });

        repository.observeSemesters().observe(getViewLifecycleOwner(), semesters -> {
            SemesterEntity current = null;
            if (semesters != null) {
                for (SemesterEntity semester : semesters) {
                    if (semester.isCurrent) {
                        current = semester;
                        break;
                    }
                }
            }
            chipCurrentSemester.setText(current == null
                    ? getString(R.string.label_no_current_semester_short)
                    : formatSemesterLabel(current));
        });

        repository.observeCurrentSemester().observe(getViewLifecycleOwner(), semester -> {
            currentSemesterId = semester == null ? 0 : semester.id;
            if (currentSemesterId == 0 && !showAllSemesters) {
                chipGroupSemester.check(R.id.chipAllSemesters);
            } else {
                refreshSubjects();
            }
        });

        subjectObserver = this::renderSubjects;
    }

    private void refreshSubjects() {
        if (subjectObserver == null || repository == null || !isAdded()) {
            return;
        }
        if (activeSubjectSource != null) {
            activeSubjectSource.removeObserver(subjectObserver);
        }

        if (showAllSemesters) {
            activeSubjectSource = repository.searchAllSubjects(query);
        } else if (currentSemesterId > 0) {
            activeSubjectSource = repository.searchSubjects(currentSemesterId, query);
        } else {
            activeSubjectSource = null;
            renderSubjects(null);
            return;
        }
        activeSubjectSource.observe(getViewLifecycleOwner(), subjectObserver);
    }

    private void renderSubjects(List<SubjectEntity> entities) {
        List<SubjectItem> items = new ArrayList<>();
        if (entities != null) {
            for (SubjectEntity entity : entities) {
                items.add(new SubjectItem(
                        String.valueOf(entity.id),
                        safe(entity.code),
                        safe(entity.name),
                        entity.credits,
                        safe(entity.defaultLecturer),
                        "",
                        safe(entity.colorKey),
                        safe(entity.note),
                        initials(entity.name)
                ));
            }
        }
        subjectAdapter.updateData(items);
        tvSubjectCountSubtitle.setText(getString(R.string.subjects_count_format, items.size()));
    }

    private String formatSemesterLabel(SemesterEntity semester) {
        if (semester.academicYear == null || semester.academicYear.trim().isEmpty()) {
            return safe(semester.name);
        }
        return getString(R.string.semester_label_format, semester.name, semester.academicYear);
    }

    private static String initials(String name) {
        String normalized = safe(name).trim();
        if (normalized.isEmpty()) {
            return "?";
        }
        String[] words = normalized.split("\\s+");
        if (words.length > 1) {
            return (words[0].substring(0, 1) + words[1].substring(0, 1)).toUpperCase(Locale.getDefault());
        }
        return normalized.substring(0, Math.min(2, normalized.length())).toUpperCase(Locale.getDefault());
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
