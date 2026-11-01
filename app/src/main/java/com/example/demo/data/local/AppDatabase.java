package com.example.demo.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.demo.data.local.dao.ClassSectionDao;
import com.example.demo.data.local.dao.ScheduleDao;
import com.example.demo.data.local.dao.ScheduleExceptionDao;
import com.example.demo.data.local.dao.SemesterDao;
import com.example.demo.data.local.dao.SubjectDao;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

@Database(
        entities = {
                SemesterEntity.class,
                SubjectEntity.class,
                ClassSectionEntity.class,
                ScheduleEntity.class,
                ScheduleExceptionEntity.class
        },
        version = 2,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "personal_schedule.db";
    private static volatile AppDatabase instance;

    /** Version 1 -> 2: adds schedule_exceptions without touching existing user data. */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `schedule_exceptions` ("
                    + "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                    + "`type` TEXT, "
                    + "`scheduleId` INTEGER, "
                    + "`classSectionId` INTEGER NOT NULL, "
                    + "`originalEpochDay` INTEGER, "
                    + "`newEpochDay` INTEGER, "
                    + "`newStartMinute` INTEGER, "
                    + "`newEndMinute` INTEGER, "
                    + "`newRoom` TEXT, "
                    + "`note` TEXT, "
                    + "`createdAtMillis` INTEGER NOT NULL, "
                    + "FOREIGN KEY(`scheduleId`) REFERENCES `schedules`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE , "
                    + "FOREIGN KEY(`classSectionId`) REFERENCES `class_sections`(`id`) "
                    + "ON UPDATE NO ACTION ON DELETE CASCADE )");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS "
                    + "`index_schedule_exceptions_scheduleId_originalEpochDay` "
                    + "ON `schedule_exceptions` (`scheduleId`, `originalEpochDay`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_schedule_exceptions_classSectionId` "
                    + "ON `schedule_exceptions` (`classSectionId`)");
            db.execSQL("CREATE INDEX IF NOT EXISTS "
                    + "`index_schedule_exceptions_newEpochDay` "
                    + "ON `schedule_exceptions` (`newEpochDay`)");
        }
    };

    public abstract SemesterDao semesterDao();

    public abstract SubjectDao subjectDao();

    public abstract ClassSectionDao classSectionDao();

    public abstract ScheduleDao scheduleDao();

    public abstract ScheduleExceptionDao scheduleExceptionDao();

    public static AppDatabase getInstance(Context context) {
        AppDatabase current = instance;
        if (current == null) {
            synchronized (AppDatabase.class) {
                current = instance;
                if (current == null) {
                    current = Room.databaseBuilder(
                                    context.getApplicationContext(),
                                    AppDatabase.class,
                                    DATABASE_NAME
                            )
                            .addMigrations(MIGRATION_1_2)
                            .build();
                    instance = current;
                }
            }
        }
        return current;
    }
}