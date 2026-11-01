package com.example.demo.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.SubjectPersistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class SubjectPersistenceTest {
    private AppDatabase database;
    private SubjectPersistence persistence;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        persistence = new SubjectPersistence(database);
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void insertNormalizesFieldsAndRejectsDuplicateCodeIgnoringCaseAndSpaces() {
        long semesterId = database.semesterDao().insert(
                new SemesterEntity("Fall", "2026-2027", null, null, true, ""));
        SubjectEntity first = new SubjectEntity(
                semesterId, " cs101 ", " Lập trình Java ", 4, " ThS. An ", " ", " note ");

        long firstId = persistence.insert(first);

        assertEquals(1, database.subjectDao().getAll().size());
        SubjectEntity stored = database.subjectDao().getAll().get(0);
        assertEquals(firstId, stored.id);
        assertEquals("CS101", stored.code);
        assertEquals("Lập trình Java", stored.name);
        assertEquals("ThS. An", stored.defaultLecturer);
        assertEquals("blue", stored.colorKey);
        assertEquals("note", stored.note);

        SubjectEntity duplicate = new SubjectEntity(
                semesterId, "CS101", "Another name", 3, "", "blue", "");
        assertThrows(SQLiteConstraintException.class, () -> persistence.insert(duplicate));
        assertEquals(1, database.subjectDao().getAll().size());
    }

    @Test
    public void updateRejectsAnotherSubjectCodeInSameSemesterButAllowsSameRow() {
        long semesterId = database.semesterDao().insert(
                new SemesterEntity("Fall", "2026-2027", null, null, true, ""));
        long firstId = persistence.insert(new SubjectEntity(
                semesterId, "CS101", "Algorithms", 3, "", "blue", ""));
        long secondId = persistence.insert(new SubjectEntity(
                semesterId, "CS202", "Networks", 3, "", "green", ""));

        SubjectEntity first = database.subjectDao().getAll().get(0);
        assertEquals(1, persistence.update(first));

        SubjectEntity second = new SubjectEntity(
                semesterId, " cs101 ", "Networks", 3, "", "green", "");
        second.id = secondId;
        assertThrows(SQLiteConstraintException.class, () -> persistence.update(second));
        assertEquals(firstId, database.subjectDao().getAll().get(0).id);
        assertEquals(2, database.subjectDao().getAll().size());
    }

    @Test
    public void rejectsMissingSemesterAndInvalidCreditsBeforeWriting() {
        assertThrows(IllegalArgumentException.class, () -> persistence.insert(
                new SubjectEntity(999L, "CS101", "Algorithms", 3, "", "blue", "")));
        long semesterId = database.semesterDao().insert(
                new SemesterEntity("Fall", "2026-2027", null, null, true, ""));
        assertThrows(IllegalArgumentException.class, () -> persistence.insert(
                new SubjectEntity(semesterId, "CS101", "Algorithms", -1, "", "blue", "")));
        assertEquals(0, database.subjectDao().getAll().size());
    }
}