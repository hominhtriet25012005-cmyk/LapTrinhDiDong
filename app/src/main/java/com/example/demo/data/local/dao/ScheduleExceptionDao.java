package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.demo.data.local.entity.ScheduleExceptionEntity;

import java.util.List;

@Dao
public interface ScheduleExceptionDao {
    @Query("SELECT * FROM schedule_exceptions ORDER BY originalDateEpochDay, id")
    List<ScheduleExceptionEntity> getAll();

    @Query("SELECT * FROM schedule_exceptions ORDER BY originalDateEpochDay, id")
    LiveData<List<ScheduleExceptionEntity>> observeAll();

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleId = :scheduleId "
            + "ORDER BY originalDateEpochDay, id")
    List<ScheduleExceptionEntity> getForSchedule(long scheduleId);

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleId = :scheduleId "
            + "ORDER BY originalDateEpochDay, id")
    LiveData<List<ScheduleExceptionEntity>> observeForSchedule(long scheduleId);

    @Query("SELECT * FROM schedule_exceptions WHERE id = :exceptionId LIMIT 1")
    ScheduleExceptionEntity getById(long exceptionId);

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleId = :scheduleId "
            + "AND originalDateEpochDay = :originalDateEpochDay AND id != :excludedId LIMIT 1")
    ScheduleExceptionEntity findOtherForOriginalOccurrence(long scheduleId,
                                                            long originalDateEpochDay,
                                                            long excludedId);

    @Query("SELECT * FROM schedule_exceptions WHERE replacementDateEpochDay = :dateEpochDay "
            + "ORDER BY replacementDateEpochDay, id")
    List<ScheduleExceptionEntity> getForReplacementDate(long dateEpochDay);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ScheduleExceptionEntity exception);

    @Update
    int update(ScheduleExceptionEntity exception);

    @Delete
    int delete(ScheduleExceptionEntity exception);

    @Query("DELETE FROM schedule_exceptions")
    void deleteAll();

    @Insert(onConflict = OnConflictStrategy.ABORT)
    void insertAll(List<ScheduleExceptionEntity> exceptions);
}
