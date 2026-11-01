package com.example.demo.data.local;

import android.content.Context;
import android.database.sqlite.SQLiteConstraintException;

import androidx.room.Room;
import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.repository.ScheduleExceptionPersistence;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;

@RunWith(AndroidJUnit4.class)
public class ScheduleExceptionMigrationTest {
    private static final String TEST_DATABASE = "schedule-exception-migration-test";
    private static final long SEMESTER_ID = 11L;
    private static final long SUBJECT_ID = 22L;
    private static final long CLASS_SECTION_ID = 33L;
    private static final long SCHEDULE_ID = 44L;
    private static final LocalDate FIRST_CLASS_DATE = LocalDate.of(2026, 3, 16);

    @Rule
    public final MigrationTestHelper migrationHelper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(), AppDatabase.class);

    @Test
    public void migratesVersionOneWithoutLosingRowsAndSupportsExceptionCascade() throws Exception {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DATABASE);

        SupportSQLiteDatabase versionOne = migrationHelper.createDatabase(TEST_DATABASE, 1);
        try {
            versionOne.execSQL("INSERT INTO semesters "
                    + "(id,name,academicYear,startDateEpochDay,endDateEpochDay,isCurrent,note) "
                    + "VALUES (11,'HK1','2025-2026',?, ?,1,'semester note')",
                    new Object[]{FIRST_CLASS_DATE.toEpochDay(), FIRST_CLASS_DATE.plusDays(30).toEpochDay()});
            versionOne.execSQL("INSERT INTO subjects "
                    + "(id,semesterId,code,name,credits,defaultLecturer,colorKey,note) "
                    + "VALUES (22,11,'CS101','Algorithms',3,'Lecturer','blue','subject note')");
            versionOne.execSQL("INSERT INTO class_sections "
                    + "(id,subjectId,classCode,lecturer,note) "
                    + "VALUES (33,22,'CS101-01','Lecturer','class note')");
            versionOne.execSQL("INSERT INTO schedules "
                    + "(id,classSectionId,dayOfWeek,startMinute,endMinute,room,validFromEpochDay,"
                    + "validToEpochDay,note,notificationEnabled,reminderMinutesBefore) "
                    + "VALUES (44,33,1,540,600,'R101',?,?, 'schedule note',1,1440)",
                    new Object[]{FIRST_CLASS_DATE.toEpochDay(), FIRST_CLASS_DATE.plusDays(30).toEpochDay()});
        } finally {
            versionOne.close();
        }

        AppDatabase migrated = Room.databaseBuilder(context, AppDatabase.class, TEST_DATABASE)
                .addMigrations(AppDatabase.MIGRATION_1_2)
                .allowMainThreadQueries()
                .build();
        try {
            assertEquals(2, migrated.getOpenHelper().getReadableDatabase().getVersion());
            assertEquals(1, migrated.semesterDao().getAll().size());
            assertEquals(SEMESTER_ID, migrated.semesterDao().getById(SEMESTER_ID).id);
            assertEquals(1, migrated.subjectDao().getAll().size());
            assertEquals("CS101", migrated.subjectDao().getAll().get(0).code);
            assertEquals(1, migrated.classSectionDao().getAll().size());
            assertEquals(1, migrated.scheduleDao().getAll().size());
            assertEquals(SCHEDULE_ID, migrated.scheduleDao().getAll().get(0).id);
            assertEquals(0, migrated.scheduleExceptionDao().getAll().size());

            ScheduleExceptionEntity cancellation = new ScheduleExceptionEntity();
            cancellation.scheduleId = SCHEDULE_ID;
            cancellation.originalDateEpochDay = FIRST_CLASS_DATE.toEpochDay();
            cancellation.exceptionType = ScheduleExceptionEntity.TYPE_CANCEL;
            ScheduleExceptionPersistence persistence = new ScheduleExceptionPersistence(migrated);
            cancellation.id = persistence.insert(cancellation);
            assertNotNull(migrated.scheduleExceptionDao().getById(cancellation.id));

            ScheduleExceptionEntity duplicate = new ScheduleExceptionEntity();
            duplicate.scheduleId = SCHEDULE_ID;
            duplicate.originalDateEpochDay = FIRST_CLASS_DATE.toEpochDay();
            duplicate.exceptionType = ScheduleExceptionEntity.TYPE_CANCEL;
            assertThrows(SQLiteConstraintException.class,
                    () -> migrated.scheduleExceptionDao().insert(duplicate));

            migrated.scheduleDao().delete(migrated.scheduleDao().getById(SCHEDULE_ID));
            assertEquals(0, migrated.scheduleExceptionDao().getAll().size());
        } finally {
            migrated.close();
            context.deleteDatabase(TEST_DATABASE);
        }
    }
}
