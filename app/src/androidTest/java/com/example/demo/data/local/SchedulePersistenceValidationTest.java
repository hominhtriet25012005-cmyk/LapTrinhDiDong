package com.example.demo.data.local;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.SchedulePersistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class SchedulePersistenceValidationTest {
    private AppDatabase database;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void normalizesClassCodeBeforeCaseInsensitiveReuse() {
        long semesterId = database.semesterDao().insert(
                new SemesterEntity("Fall", "2026-2027", null, null, true, ""));
        long subjectId = database.subjectDao().insert(
                new SubjectEntity(semesterId, "CS101", "Algorithms", 3, "", "blue", ""));
        SchedulePersistence persistence = new SchedulePersistence(database);

        long firstId = persistence.saveClassSection(
                new ClassSectionEntity(subjectId, " cs101-01 ", " Dr. A ", " note "));
        long reusedId = persistence.saveClassSection(
                new ClassSectionEntity(subjectId, "CS101-01", " Dr. B ", ""));

        assertEquals(firstId, reusedId);
        assertEquals(1, database.classSectionDao().getAll().size());
        assertEquals("CS101-01", database.classSectionDao().getById(firstId).classCode);
        assertEquals("Dr. B", database.classSectionDao().getById(firstId).lecturer);
    }
}