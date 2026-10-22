package com.example.demo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.example.demo.model.ScheduleItem;
import com.example.demo.notifications.NotificationScheduler;
import com.example.demo.ui.home.HomeFragment;
import com.example.demo.ui.settings.SettingsFragment;
import com.example.demo.ui.subjects.SubjectsFragment;
import com.example.demo.ui.subject_detail.SubjectDetailActivity;
import com.example.demo.ui.timetable.TimetableFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNavigation;
    private final Fragment homeFragment = new HomeFragment();
    private final Fragment timetableFragment = new TimetableFragment();
    private final Fragment subjectsFragment = new SubjectsFragment();
    private final Fragment settingsFragment = new SettingsFragment();
    private Fragment activeFragment = homeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainLayout), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0);
            return insets;
        });

        bottomNavigation = findViewById(R.id.bottom_navigation);

        FragmentManager fm = getSupportFragmentManager();
        fm.beginTransaction()
                .add(R.id.fragment_container, settingsFragment, "4").hide(settingsFragment)
                .add(R.id.fragment_container, subjectsFragment, "3").hide(subjectsFragment)
                .add(R.id.fragment_container, timetableFragment, "2").hide(timetableFragment)
                .add(R.id.fragment_container, homeFragment, "1")
                .commit();

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_home) {
                switchFragment(homeFragment);
                return true;
            } else if (itemId == R.id.menu_timetable) {
                switchFragment(timetableFragment);
                return true;
            } else if (itemId == R.id.menu_subjects) {
                switchFragment(subjectsFragment);
                return true;
            } else if (itemId == R.id.menu_settings) {
                switchFragment(settingsFragment);
                return true;
            }
            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationScheduler.syncAll(getApplicationContext());
    }

    private void switchFragment(Fragment targetFragment) {
        if (activeFragment != targetFragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(targetFragment)
                    .commit();
            activeFragment = targetFragment;
        }
    }

    public void navigateToTab(int menuId) {
        bottomNavigation.setSelectedItemId(menuId);
    }

    // Helper navigation stubs (will open respective Activities when implemented in Phase 3/4)
    public void openScheduleDetail(ScheduleItem item) {
        if (item == null) {
            return;
        }
        try {
            openScheduleDetail(Long.parseLong(item.getId()));
        } catch (NumberFormatException exception) {
            Toast.makeText(this, R.string.error_schedule_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    public void openScheduleDetail(long scheduleId) {
        Intent intent = new Intent(this, com.example.demo.ui.detail.ScheduleDetailActivity.class);
        intent.putExtra(com.example.demo.ui.detail.ScheduleDetailActivity.EXTRA_SCHEDULE_ID, scheduleId);
        startActivity(intent);
    }

    public void openAddSchedule() {
        launchSubScreen("com.example.demo.ui.form.ScheduleFormActivity", null, null);
    }

    public void openSubjectDetail(long subjectId) {
        Intent intent = new Intent(this, SubjectDetailActivity.class);
        intent.putExtra(SubjectDetailActivity.EXTRA_SUBJECT_ID, subjectId);
        startActivity(intent);
    }

    public void openAddSubject() {
        launchSubScreen("com.example.demo.ui.subject_form.SubjectFormActivity", null, null);
    }

    public void openSemesters() {
        launchSubScreen("com.example.demo.ui.semester.SemestersActivity", null, null);
    }

    public void openStats() {
        launchSubScreen("com.example.demo.ui.stats.StatsActivity", null, null);
    }

    public void openSearch() {
        launchSubScreen("com.example.demo.ui.search.SearchActivity", null, null);
    }

    public void openNotifications() {
        launchSubScreen("com.example.demo.ui.notifications.NotificationsActivity", null, null);
    }

    public void openExport() {
        launchSubScreen("com.example.demo.ui.export.ExportActivity", null, null);
    }

    public void openBackup() {
        launchSubScreen("com.example.demo.ui.backup.BackupActivity", null, null);
    }

    public void openStates() {
        launchSubScreen("com.example.demo.ui.states.StatesActivity", null, null);
    }

    private void launchSubScreen(String className, String extraKey, java.io.Serializable extraValue) {
        try {
            Class<?> clazz = Class.forName(className);
            Intent intent = new Intent(this, clazz);
            if (extraKey != null && extraValue != null) {
                intent.putExtra(extraKey, extraValue);
            }
            startActivity(intent);
        } catch (ClassNotFoundException e) {
            Toast.makeText(this, "Màn hình sẽ sẵn sàng ở Giai đoạn tiếp theo", Toast.LENGTH_SHORT).show();
        }
    }
}
