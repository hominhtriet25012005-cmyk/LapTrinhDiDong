package com.example.demo.data.local;

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
import com.example.demo.data.repository.SchedulePersistence;
import com.example.demo.data.validation.ScheduleConflictException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertNull;

@RunWith(AndroidJUnit4.class)
public class ScheduleExceptionPersistenceTest {
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 16);
    private AppDatabase database;
    private ScheduleExceptionPersistence exceptionPersistence;
    private long mondayScheduleId;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        exceptionPersistence = new ScheduleExceptionPersistence(database);
        long semesterId = database.semesterDao().insert(
                new SemesterEntity("HK1", "2025-2026", null, null, true, ""));
        long mondaySubjectId = database.subjectDao().insert(
                new SubjectEntity(semesterId, "CS101", "Algorithms", 3, "", "blue", ""));
        long tuesdaySubjectId = database.subjectDao().insert(
                new SubjectEntity(semesterId, "CS202", "Networks", 3, "", "blue", ""));
        SchedulePersistence schedulePersistence = new SchedulePersistence(database);
        mondayScheduleId = insertSchedule(schedulePersistence, mondaySubjectId,
                "CS101-01", 1, 540, 600, MONDAY, MONDAY.plusDays(21));
        insertSchedule(schedulePersistence, tuesdaySubjectId,
                "CS202-01", 2, 630, 690, MONDAY, MONDAY.plusDays(21));
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void preventsDuplicateOriginalExceptionsAndScheduleDeleteCascades() {
        ScheduleExceptionEntity cancellation = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_CANCEL);
        long exceptionId = exceptionPersistence.insert(cancellation);

        assertEquals(1, database.scheduleExceptionDao().getForSchedule(mondayScheduleId).size());
        assertThrows(IllegalArgumentException.class,
                () -> exceptionPersistence.insert(exception(
                        mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_CANCEL)));

        database.scheduleDao().delete(database.scheduleDao().getById(mondayScheduleId));
        assertEquals(0, database.scheduleExceptionDao().getForSchedule(mondayScheduleId).size());
        assertEquals(0, database.scheduleExceptionDao().getAll().size());
        assertNull(database.scheduleExceptionDao().getById(exceptionId));
        cancellation.id = exceptionId;
        assertEquals(0, exceptionPersistence.delete(cancellation));
    }

    @Test
    public void rejectsConflictingMakeupWithoutWritingIt() {
        ScheduleExceptionEntity makeup = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_MAKEUP);
        makeup.replacementDateEpochDay = MONDAY.plusDays(1).toEpochDay();
        makeup.startMinute = 600;
        makeup.endMinute = 660;

        assertThrows(ScheduleConflictException.class,
                () -> exceptionPersistence.insert(makeup));
        assertEquals(0, database.scheduleExceptionDao().getAll().size());
    }

    @Test
    public void conflictingUpdateRollsBackAndKeepsExistingException() {
        ScheduleExceptionEntity cancellation = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_CANCEL);
        cancellation.id = exceptionPersistence.insert(cancellation);
        cancellation.exceptionType = ScheduleExceptionEntity.TYPE_MAKEUP;
        cancellation.replacementDateEpochDay = MONDAY.plusDays(1).toEpochDay();
        cancellation.startMinute = 600;
        cancellation.endMinute = 660;

        assertThrows(ScheduleConflictException.class,
                () -> exceptionPersistence.update(cancellation));

        ScheduleExceptionEntity saved = database.scheduleExceptionDao().getById(cancellation.id);
        assertEquals(ScheduleExceptionEntity.TYPE_CANCEL, saved.exceptionType);
        assertNull(saved.replacementDateEpochDay);
    }

    @Test
    public void rejectsRecurringScheduleEditThatInvalidatesItsOriginalExceptionDate() {
        ScheduleExceptionEntity cancellation = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_CANCEL);
        cancellation.id = exceptionPersistence.insert(cancellation);
        ScheduleEntity schedule = database.scheduleDao().getById(mondayScheduleId);
        schedule.dayOfWeek = 2;

        assertThrows(IllegalArgumentException.class,
                () -> new SchedulePersistence(database).saveSchedule(schedule));

        assertEquals(1, database.scheduleDao().getById(mondayScheduleId).dayOfWeek);
        assertEquals(1, database.scheduleExceptionDao().getForSchedule(mondayScheduleId).size());
    }

    @Test
    public void rejectsNewRecurringScheduleThatConflictsWithExistingMakeup() {
        ScheduleExceptionEntity makeup = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_MAKEUP);
        makeup.replacementDateEpochDay = MONDAY.plusDays(2).toEpochDay();
        exceptionPersistence.insert(makeup);

        SchedulePersistence schedulePersistence = new SchedulePersistence(database);
        assertThrows(ScheduleConflictException.class,
                () -> insertSchedule(schedulePersistence,
                        database.subjectDao().getAll().get(0).id, "CS101-02", 3,
                        570, 630, MONDAY, MONDAY.plusDays(21)));

        assertEquals(2, database.scheduleDao().getAll().size());
    }

    @Test
    public void rejectsRecurringScheduleEditThatConflictsWithExistingMakeup() {
        ScheduleExceptionEntity makeup = exception(
                mondayScheduleId, MONDAY, ScheduleExceptionEntity.TYPE_MAKEUP);
        makeup.replacementDateEpochDay = MONDAY.plusDays(2).toEpochDay();
        exceptionPersistence.insert(makeup);

        ScheduleEntity tuesdaySchedule = database.scheduleDao().getForDayOfWeek(2).get(0);
        tuesdaySchedule.dayOfWeek = 3;
        tuesdaySchedule.startMinute = 570;
        tuesdaySchedule.endMinute = 630;

        assertThrows(ScheduleConflictException.class,
                () -> new SchedulePersistence(database).saveSchedule(tuesdaySchedule));

        assertEquals(2, database.scheduleDao().getById(tuesdaySchedule.id).dayOfWeek);
    }

    private long insertSchedule(SchedulePersistence persistence, long subjectId, String classCode,
                                int day, int start, int end, LocalDate from, LocalDate to) {
        ClassSectionEntity section = new ClassSectionEntity(subjectId, classCode, "", "");
        ScheduleEntity schedule = new ScheduleEntity(0L, day, start, end, "R101",
                from.toEpochDay(), to.toEpochDay(), "");
        return persistence.saveScheduleWithClassSection(section, schedule);
    }

    private static ScheduleExceptionEntity exception(long scheduleId, LocalDate original,
                                                      String type) {
        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.scheduleId = scheduleId;
        exception.originalDateEpochDay = original.toEpochDay();
        exception.exceptionType = type;
        return exception;
    }
}
