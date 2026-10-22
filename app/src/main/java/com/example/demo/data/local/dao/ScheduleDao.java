package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.demo.data.local.entity.ScheduleEntity;
import com.example.demo.data.local.projection.ScheduleDetails;

import java.util.List;

@Dao
public interface ScheduleDao {
    @Query("SELECT * FROM schedules ORDER BY dayOfWeek, startMinute, id")
    List<ScheduleEntity> getAll();

    @Query("UPDATE schedules SET notificationEnabled = :enabled")
    int setNotificationEnabledForAll(boolean enabled);

    @Query("SELECT * FROM schedules WHERE classSectionId = :classSectionId ORDER BY dayOfWeek, startMinute")
    LiveData<List<ScheduleEntity>> observeForClassSection(long classSectionId);

    @Query("SELECT * FROM schedules WHERE id = :scheduleId LIMIT 1")
    LiveData<ScheduleEntity> observeById(long scheduleId);

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute, id")
    LiveData<List<ScheduleEntity>> observeForDayOfWeek(int dayOfWeek);

    @Query("SELECT * FROM schedules WHERE dayOfWeek = :dayOfWeek ORDER BY startMinute, id")
    List<ScheduleEntity> getForDayOfWeek(int dayOfWeek);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ScheduleEntity schedule);

    @Update
    int update(ScheduleEntity schedule);

    @Delete
    int delete(ScheduleEntity schedule);

    @Transaction
    @Query("SELECT "
            + "sch.id AS scheduleId, sch.classSectionId AS classSectionId, "
            + "sch.dayOfWeek AS dayOfWeek, sch.startMinute AS startMinute, "
            + "sch.endMinute AS endMinute, sch.room AS room, "
            + "sch.validFromEpochDay AS validFromEpochDay, "
            + "sch.validToEpochDay AS validToEpochDay, sch.note AS note, "
            + "sch.notificationEnabled AS notificationEnabled, "
            + "sch.reminderMinutesBefore AS reminderMinutesBefore, "
            + "cs.classCode AS classCode, cs.lecturer AS lecturer, "
            + "sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, sub.colorKey AS subjectColorKey, "
            + "sem.id AS semesterId, sem.name AS semesterName, sem.academicYear AS semesterAcademicYear "
            + "FROM schedules AS sch "
            + "INNER JOIN class_sections AS cs ON cs.id = sch.classSectionId "
            + "INNER JOIN subjects AS sub ON sub.id = cs.subjectId "
            + "INNER JOIN semesters AS sem ON sem.id = sub.semesterId "
            + "ORDER BY sch.dayOfWeek, sch.startMinute, sch.id")
    List<ScheduleDetails> getAllDetails();

    @Transaction
    @Query("SELECT "
            + "sch.id AS scheduleId, sch.classSectionId AS classSectionId, "
            + "sch.dayOfWeek AS dayOfWeek, sch.startMinute AS startMinute, "
            + "sch.endMinute AS endMinute, sch.room AS room, "
            + "sch.validFromEpochDay AS validFromEpochDay, "
            + "sch.validToEpochDay AS validToEpochDay, sch.note AS note, "
            + "sch.notificationEnabled AS notificationEnabled, "
            + "sch.reminderMinutesBefore AS reminderMinutesBefore, "
            + "cs.classCode AS classCode, cs.lecturer AS lecturer, "
            + "sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, sub.colorKey AS subjectColorKey, "
            + "sem.id AS semesterId, sem.name AS semesterName, sem.academicYear AS semesterAcademicYear "
            + "FROM schedules AS sch "
            + "INNER JOIN class_sections AS cs ON cs.id = sch.classSectionId "
            + "INNER JOIN subjects AS sub ON sub.id = cs.subjectId "
            + "INNER JOIN semesters AS sem ON sem.id = sub.semesterId "
            + "ORDER BY sch.dayOfWeek, sch.startMinute, sch.id")
    LiveData<List<ScheduleDetails>> observeAllDetails();

    @Transaction
    @Query("SELECT "
            + "sch.id AS scheduleId, sch.classSectionId AS classSectionId, "
            + "sch.dayOfWeek AS dayOfWeek, sch.startMinute AS startMinute, "
            + "sch.endMinute AS endMinute, sch.room AS room, "
            + "sch.validFromEpochDay AS validFromEpochDay, "
            + "sch.validToEpochDay AS validToEpochDay, sch.note AS note, "
            + "sch.notificationEnabled AS notificationEnabled, "
            + "sch.reminderMinutesBefore AS reminderMinutesBefore, "
            + "cs.classCode AS classCode, cs.lecturer AS lecturer, "
            + "sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, sub.colorKey AS subjectColorKey, "
            + "sem.id AS semesterId, sem.name AS semesterName, sem.academicYear AS semesterAcademicYear "
            + "FROM schedules AS sch "
            + "INNER JOIN class_sections AS cs ON cs.id = sch.classSectionId "
            + "INNER JOIN subjects AS sub ON sub.id = cs.subjectId "
            + "INNER JOIN semesters AS sem ON sem.id = sub.semesterId "
            + "WHERE (:semesterId <= 0 OR sem.id = :semesterId) "
            + "AND (:dayOfWeek <= 0 OR sch.dayOfWeek = :dayOfWeek) "
            + "AND (:query = '' "
            + "OR COALESCE(sub.code, '') LIKE '%' || :query || '%' COLLATE NOCASE "
            + "OR COALESCE(sub.name, '') LIKE '%' || :query || '%' COLLATE NOCASE "
            + "OR COALESCE(cs.classCode, '') LIKE '%' || :query || '%' COLLATE NOCASE "
            + "OR COALESCE(sch.room, '') LIKE '%' || :query || '%' COLLATE NOCASE) "
            + "AND (:roomQuery = '' OR COALESCE(sch.room, '') LIKE '%' || :roomQuery || '%' COLLATE NOCASE) "
            + "ORDER BY sch.dayOfWeek, sch.startMinute, sch.id")
    LiveData<List<ScheduleDetails>> searchDetails(String query, long semesterId,
                                                  int dayOfWeek, String roomQuery);

    @Transaction
    @Query("SELECT "
            + "sch.id AS scheduleId, sch.classSectionId AS classSectionId, "
            + "sch.dayOfWeek AS dayOfWeek, sch.startMinute AS startMinute, "
            + "sch.endMinute AS endMinute, sch.room AS room, "
            + "sch.validFromEpochDay AS validFromEpochDay, "
            + "sch.validToEpochDay AS validToEpochDay, sch.note AS note, "
            + "sch.notificationEnabled AS notificationEnabled, "
            + "sch.reminderMinutesBefore AS reminderMinutesBefore, "
            + "cs.classCode AS classCode, cs.lecturer AS lecturer, "
            + "sub.id AS subjectId, sub.code AS subjectCode, sub.name AS subjectName, sub.colorKey AS subjectColorKey, "
            + "sem.id AS semesterId, sem.name AS semesterName, sem.academicYear AS semesterAcademicYear "
            + "FROM schedules AS sch "
            + "INNER JOIN class_sections AS cs ON cs.id = sch.classSectionId "
            + "INNER JOIN subjects AS sub ON sub.id = cs.subjectId "
            + "INNER JOIN semesters AS sem ON sem.id = sub.semesterId "
            + "WHERE sch.id = :scheduleId LIMIT 1")
    LiveData<ScheduleDetails> observeDetailsById(long scheduleId);
}
