package com.example.demo;

import android.view.View;

import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public class MainActivityThemeRegressionTest {

    @Test
    public void recreationRestoresExistingTabsWithoutAddingDuplicateFragments() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.navigateToTab(R.id.menu_settings);
                activity.getSupportFragmentManager().executePendingTransactions();
            });

            scenario.recreate();

            scenario.onActivity(activity -> {
                assertEquals(R.id.menu_settings,
                        ((BottomNavigationView) activity.findViewById(R.id.bottom_navigation))
                                .getSelectedItemId());
                assertEquals(4, activity.getSupportFragmentManager().getFragments().size());
                for (String tag : new String[]{"1", "2", "3", "4"}) {
                    Fragment fragment = activity.getSupportFragmentManager().findFragmentByTag(tag);
                    assertNotNull("Expected one restored tab fragment for tag " + tag, fragment);
                    assertEquals("Restored tab should still belong to this activity", activity,
                            fragment.getActivity());
                }
            });
        }
    }

    @Test
    public void appliesSystemBarInsetsOnTheFirstActivityLaunch() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View mainLayout = activity.findViewById(R.id.mainLayout);
                WindowInsetsCompat insets = ViewCompat.getRootWindowInsets(mainLayout);

                assertNotNull("System bar insets should be available at startup", insets);
                assertEquals(insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom,
                        mainLayout.getPaddingBottom());
            });
        }
    }
}
