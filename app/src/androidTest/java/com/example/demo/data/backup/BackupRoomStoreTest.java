package com.example.demo.data.backup;

import android.content.Context;

import androidx.room.Room;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.AppDatabase;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class BackupRoomStoreTest {
    private AppDatabase database;
    private BackupRoomStore store;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase.class)
                .allowMainThreadQueries()
                .build();
        store = new BackupRoomStore();
    }

    @After
    public void tearDown() {
        if (database != null) {
            database.close();
        }
    }

    @Test
    public void restoresAllLearningTablesAndPreservesIds() {
        BackupData backup = sampleBackup();

        store.restore(database, backup);
        BackupData snapshot = store.createSnapshot(database);

        assertEquals(1, snapshot.semesters.size());
        assertEquals(10L, snapshot.semesters.get(0).id);
        assertEquals(1, snapshot.subjects.size());
        assertEquals(20L, snapshot.subjects.get(0).id);
        assertEquals(1, snapshot.classSections.size());
        assertEquals(30L, snapshot.classSections.get(0).id);
        assertEquals(1, snapshot.schedules.size());
        assertEquals(40L, snapshot.schedules.get(0).id);
        assertEquals(1, snapshot.scheduleExceptions.size());
        assertEquals(50L, snapshot.scheduleExceptions.get(0).id);
        assertEquals(40L, snapshot.scheduleExceptions.get(0).scheduleId);
    }

    @Test
    public void failedInsertRollsBackDeletedRowsAndInsertedParents() {
        long originalSemesterId = database.semesterDao().insert(
                new SemesterEntity("Giữ lại", "2024-2025", null, null, true, ""));
        database.getOpenHelper().getWritableDatabase().execSQL(
                "CREATE TRIGGER fail_backup_subject BEFORE INSERT ON subjects "
                        + "BEGIN SELECT RAISE(ABORT, 'forced backup restore failure'); END");

        assertThrows(RuntimeException.class, () -> store.restore(database, sampleBackup()));

        assertEquals(1, database.semesterDao().getAll().size());
        assertEquals(originalSemesterId, database.semesterDao().getAll().get(0).id);
        assertEquals("Giữ lại", database.semesterDao().getById(originalSemesterId).name);
        assertEquals(0, database.subjectDao().getAll().size());
        assertEquals(0, database.classSectionDao().getAll().size());
        assertEquals(0, database.scheduleDao().getAll().size());
        assertEquals(0, database.scheduleExceptionDao().getAll().size());
    }

    private static BackupData sampleBackup() {
        LocalDate monday = LocalDate.of(2026, 11, 2);
        SemesterEntity semester = new SemesterEntity(
                "HK 1", "2026-2027", monday.toEpochDay(), monday.plusMonths(1).toEpochDay(),
                true, "backup note");
        semester.id = 10L;

        SubjectEntity subject = new SubjectEntity(
                10L, "CS101", "Lập trình Android", 3, "Giảng viên", "blue", "Ghi chú");
        subject.id = 20L;

        ClassSectionEntity classSection = new ClassSectionEntity(
                20L, "CS101-01", "Giảng viên", "Lớp học phần");
        classSection.id = 30L;

        ScheduleEntity schedule = new ScheduleEntity(
                30L, 1, 540, 600, "A101", monday.toEpochDay(),
                monday.plusWeeks(4).toEpochDay(), "Buổi học");
        schedule.id = 40L;
        schedule.notificationEnabled = true;

        ScheduleExceptionEntity exception = new ScheduleExceptionEntity();
        exception.id = 50L;
        exception.scheduleId = 40L;
        exception.originalDateEpochDay = monday.toEpochDay();
        exception.exceptionType = ScheduleExceptionEntity.TYPE_CANCEL;
        exception.note = "Nghỉ lễ";

        return BackupData.create(System.currentTimeMillis(),
                Collections.singletonList(semester),
                Collections.singletonList(subject),
                Collections.singletonList(classSection),
                Collections.singletonList(schedule),
                Collections.singletonList(exception));
    }
}
