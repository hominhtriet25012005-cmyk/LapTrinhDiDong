package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.entity.ScheduleExceptionEntity;
import com.example.demo.data.local.projection.ClassSectionDetails;

import java.util.List;

@Dao
public interface ScheduleExceptionDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ScheduleExceptionEntity exception);

    @Update
    int update(ScheduleExceptionEntity exception);

    @Query("DELETE FROM schedule_exceptions WHERE id = :id")
    int deleteById(long id);

    @Query("SELECT * FROM schedule_exceptions WHERE id = :id LIMIT 1")
    ScheduleExceptionEntity getById(long id);

    @Query("SELECT * FROM schedule_exceptions "
            + "WHERE scheduleId = :scheduleId AND originalEpochDay = :originalEpochDay LIMIT 1")
    ScheduleExceptionEntity findForOccurrence(long scheduleId, long originalEpochDay);

    @Query("SELECT * FROM schedule_exceptions WHERE scheduleId = :scheduleId ORDER BY originalEpochDay, id")
    List<ScheduleExceptionEntity> getForSchedule(long scheduleId);

    @Query("SELECT * FROM schedule_exceptions ORDER BY COALESCE(newEpochDay, originalEpochDay), id")
    List<ScheduleExceptionEntity> getAll();

    @Query("SELECT * FROM schedule_exceptions ORDER BY COALESCE(newEpochDay, originalEpochDay), id")
    LiveData<List<ScheduleExceptionEntity>> observeAll();

    /** Read-only lookup used to verify the original occurrence inside the same transaction. */
    @Query("SELECT * FROM schedules WHERE id = :scheduleId LIMIT 1")
    ScheduleEntity findScheduleById(long scheduleId);

    @Query("SELECT cs.id AS classSectionId, cs.classCode AS classCode, cs.lecturer AS lecturer, "
            + "sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, "
            + "sub.colorKey AS subjectColorKey, "
            + "sem.id AS semesterId, sem.name AS semesterName, sem.academicYear AS semesterAcademicYear "
            + "FROM class_sections AS cs "
            + "INNER JOIN subjects AS sub ON sub.id = cs.subjectId "
            + "INNER JOIN semesters AS sem ON sem.id = sub.semesterId "
            + "ORDER BY cs.id")
    List<ClassSectionDetails> getAllClassSectionDetails();
}