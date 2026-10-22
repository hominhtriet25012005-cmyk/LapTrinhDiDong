package com.example.demo.data.local;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.demo.data.local.dao.ClassSectionDao;
import com.example.demo.data.local.dao.ScheduleDao;
import com.example.demo.data.local.dao.SemesterDao;
import com.example.demo.data.local.dao.SubjectDao;
import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.entity.SubjectEntity;

@Database(
        entities = {
                SemesterEntity.class,
                SubjectEntity.class,
                ClassSectionEntity.class,
                ScheduleEntity.class
        },
        version = 1,
        exportSchema = true
)
public abstract class AppDatabase extends RoomDatabase {
    private static final String DATABASE_NAME = "personal_schedule.db";
    private static volatile AppDatabase instance;

    public abstract SemesterDao semesterDao();

    public abstract SubjectDao subjectDao();

    public abstract ClassSectionDao classSectionDao();

    public abstract ScheduleDao scheduleDao();

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
                            .build();
                    instance = current;
                }
            }
        }
        return current;
    }
}
