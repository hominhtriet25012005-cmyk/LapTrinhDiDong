package com.example.demo;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
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
    private static final String TAG_HOME = "1";
    private static final String TAG_TIMETABLE = "2";
    private static final String TAG_SUBJECTS = "3";
    private static final String TAG_SETTINGS = "4";
    private static final String STATE_SELECTED_MENU_ITEM = "selected_menu_item";

    private BottomNavigationView bottomNavigation;
    private Fragment homeFragment;
    private Fragment timetableFragment;
    private Fragment subjectsFragment;
    private Fragment settingsFragment;
    private Fragment activeFragment;
    private int selectedMenuItemId = R.id.menu_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        View mainLayout = findViewById(R.id.mainLayout);
        ViewCompat.setOnApplyWindowInsetsListener(mainLayout, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        mainLayout.post(() -> ViewCompat.requestApplyInsets(mainLayout));

        bottomNavigation = findViewById(R.id.bottom_navigation);

        FragmentManager fm = getSupportFragmentManager();
        if (savedInstanceState == null) {
            homeFragment = new HomeFragment();
            timetableFragment = new TimetableFragment();
            subjectsFragment = new SubjectsFragment();
            settingsFragment = new SettingsFragment();
            activeFragment = homeFragment;

            fm.beginTransaction()
                    .add(R.id.fragment_container, settingsFragment, TAG_SETTINGS).hide(settingsFragment)
                    .add(R.id.fragment_container, subjectsFragment, TAG_SUBJECTS).hide(subjectsFragment)
                    .add(R.id.fragment_container, timetableFragment, TAG_TIMETABLE).hide(timetableFragment)
                    .add(R.id.fragment_container, homeFragment, TAG_HOME)
                    .commit();
        } else {
            homeFragment = fm.findFragmentByTag(TAG_HOME);
            timetableFragment = fm.findFragmentByTag(TAG_TIMETABLE);
            subjectsFragment = fm.findFragmentByTag(TAG_SUBJECTS);
            settingsFragment = fm.findFragmentByTag(TAG_SETTINGS);
            if (homeFragment == null) {
                homeFragment = new HomeFragment();
            }
            if (timetableFragment == null) {
                timetableFragment = new TimetableFragment();
            }
            if (subjectsFragment == null) {
                subjectsFragment = new SubjectsFragment();
            }
            if (settingsFragment == null) {
                settingsFragment = new SettingsFragment();
            }
            selectedMenuItemId = savedInstanceState.getInt(
                    STATE_SELECTED_MENU_ITEM, R.id.menu_home);
            activeFragment = getFragmentForMenuItem(selectedMenuItemId);
            if (activeFragment == null) {
                selectedMenuItemId = R.id.menu_home;
                activeFragment = homeFragment;
            }
            addAnyMissingFragments(fm);
        }

        bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            Fragment targetFragment = getFragmentForMenuItem(itemId);
            if (targetFragment != null) {
                selectedMenuItemId = itemId;
                switchFragment(targetFragment);
                return true;
            }
            return false;
        });
        bottomNavigation.setSelectedItemId(selectedMenuItemId);
    }

    private void addAnyMissingFragments(FragmentManager fragmentManager) {
        if (homeFragment.isAdded() && timetableFragment.isAdded()
                && subjectsFragment.isAdded() && settingsFragment.isAdded()) {
            return;
        }

        androidx.fragment.app.FragmentTransaction transaction = fragmentManager.beginTransaction();
        addIfMissing(transaction, homeFragment, TAG_HOME);
        addIfMissing(transaction, timetableFragment, TAG_TIMETABLE);
        addIfMissing(transaction, subjectsFragment, TAG_SUBJECTS);
        addIfMissing(transaction, settingsFragment, TAG_SETTINGS);
        transaction.show(activeFragment);
        transaction.commit();
    }

    private void addIfMissing(androidx.fragment.app.FragmentTransaction transaction,
                              Fragment fragment, String tag) {
        if (!fragment.isAdded()) {
            transaction.add(R.id.fragment_container, fragment, tag).hide(fragment);
        }
    }

    private Fragment getFragmentForMenuItem(int itemId) {
        if (itemId == R.id.menu_home) {
            return homeFragment;
        } else if (itemId == R.id.menu_timetable) {
            return timetableFragment;
        } else if (itemId == R.id.menu_subjects) {
            return subjectsFragment;
        } else if (itemId == R.id.menu_settings) {
            return settingsFragment;
        }
        return null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        NotificationScheduler.syncAll(getApplicationContext());
    }

    private void switchFragment(Fragment targetFragment) {
        if (activeFragment != null && activeFragment != targetFragment) {
            getSupportFragmentManager().beginTransaction()
                    .hide(activeFragment)
                    .show(targetFragment)
                    .commit();
        }
        activeFragment = targetFragment;
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putInt(STATE_SELECTED_MENU_ITEM, selectedMenuItemId);
        super.onSaveInstanceState(outState);
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
