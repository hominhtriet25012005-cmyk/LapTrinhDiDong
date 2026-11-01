package com.example.demo.data.local;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.repository.ScheduleExceptionPersistence;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class ScheduleExceptionPersistenceTest {
    private static final long MONDAY = LocalDate.of(2026, 9, 7).toEpochDay();

    private AppDatabase database;
    private ScheduleExceptionPersistence persistence;
    private long classSectionId;
    private long scheduleId;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        persistence = new ScheduleExceptionPersistence(database);

        long semesterId = database.semesterDao().insert(
                new SemesterEntity("HK1", "2026-2027", MONDAY - 14, MONDAY + 120, true, ""));
        long subjectId = database.subjectDao().insert(
                new SubjectEntity(semesterId, "IT101", "Lap trinh di dong", 3, "", "blue", ""));
        classSectionId = database.classSectionDao().insert(
                new ClassSectionEntity(subjectId, "L01", "", ""));
        scheduleId = database.scheduleDao().insert(new ScheduleEntity(
                classSectionId, 1, 7 * 60, 9 * 60, "A101", MONDAY - 7, MONDAY + 70, ""));
    }

    @After
    public void tearDown() {
        database.close();
    }

    @Test
    public void cancelThenModifySameOccurrenceKeepsSingleRow() {
        long cancelId = persistence.save(
                ScheduleExceptionEntity.cancel(scheduleId, classSectionId, MONDAY, "Nghi"));
        long modifyId = persistence.save(ScheduleExceptionEntity.modify(
                scheduleId, classSectionId, MONDAY, null, 8 * 60, 10 * 60, "B2", "Doi gio"));

        assertEquals(cancelId, modifyId);
        List<ScheduleExceptionEntity> all = database.scheduleExceptionDao().getAll();
        assertEquals(1, all.size());
        assertEquals(ScheduleExceptionEntity.TYPE_MODIFY, all.get(0).type);
        assertEquals("B2", all.get(0).newRoom);
    }

    @Test
    public void makeupSessionsAreIndependentOfOriginalSchedule() {
        persistence.save(ScheduleExceptionEntity.makeup(
                classSectionId, MONDAY + 1, 13 * 60, 15 * 60, "C3", "Hoc bu 1"));
        persistence.save(ScheduleExceptionEntity.makeup(
                classSectionId, MONDAY + 2, 13 * 60, 15 * 60, "C3", "Hoc bu 2"));

        List<ScheduleExceptionEntity> all = database.scheduleExceptionDao().getAll();
        assertEquals(2, all.size());
        assertNull(all.get(0).scheduleId);
        assertNull(all.get(0).originalEpochDay);
    }

    @Test
    public void exceptionOnWrongWeekdayIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> persistence.save(
                ScheduleExceptionEntity.cancel(scheduleId, classSectionId, MONDAY + 1, "")));
        assertEquals(0, database.scheduleExceptionDao().getAll().size());
    }

    @Test
    public void deletingScheduleRemovesItsCancelButKeepsMakeup() {
        persistence.save(ScheduleExceptionEntity.cancel(scheduleId, classSectionId, MONDAY, ""));
        persistence.save(ScheduleExceptionEntity.makeup(
                classSectionId, MONDAY + 1, 13 * 60, 15 * 60, "", ""));

        ScheduleEntity schedule = database.scheduleDao().getAll().get(0);
        database.scheduleDao().delete(schedule);

        List<ScheduleExceptionEntity> remaining = database.scheduleExceptionDao().getAll();
        assertEquals(1, remaining.size());
        assertEquals(ScheduleExceptionEntity.TYPE_MAKEUP, remaining.get(0).type);
    }

    @Test
    public void deletingClassSectionRemovesAllExceptions() {
        persistence.save(ScheduleExceptionEntity.cancel(scheduleId, classSectionId, MONDAY, ""));
        persistence.save(ScheduleExceptionEntity.makeup(
                classSectionId, MONDAY + 1, 13 * 60, 15 * 60, "", ""));

        ClassSectionEntity classSection = database.classSectionDao().getById(classSectionId);
        assertNotNull(classSection);
        database.classSectionDao().delete(classSection);

        assertEquals(0, database.scheduleExceptionDao().getAll().size());
    }
}