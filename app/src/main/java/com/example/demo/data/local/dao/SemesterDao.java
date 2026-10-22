package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.demo.data.local.entity.SemesterEntity;
import com.example.demo.data.local.projection.SemesterSummary;
import com.example.demo.data.local.relation.SemesterWithSubjects;

import java.util.List;

@Dao
public abstract class SemesterDao {
    @Query("SELECT * FROM semesters ORDER BY startDateEpochDay DESC, id DESC")
    public abstract List<SemesterEntity> getAll();

    @Query("SELECT * FROM semesters ORDER BY startDateEpochDay DESC, id DESC")
    public abstract LiveData<List<SemesterEntity>> observeAll();

    @Query("SELECT semesters.*, COUNT(subjects.id) AS subjectCount "
            + "FROM semesters LEFT JOIN subjects ON subjects.semesterId = semesters.id "
            + "GROUP BY semesters.id "
            + "ORDER BY semesters.startDateEpochDay DESC, semesters.id DESC")
    public abstract LiveData<List<SemesterSummary>> observeSummaries();

    @Query("SELECT semesters.*, COUNT(subjects.id) AS subjectCount "
            + "FROM semesters LEFT JOIN subjects ON subjects.semesterId = semesters.id "
            + "GROUP BY semesters.id "
            + "ORDER BY semesters.startDateEpochDay DESC, semesters.id DESC")
    public abstract List<SemesterSummary> getSummaries();

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    public abstract LiveData<SemesterEntity> observeById(long semesterId);

    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    public abstract SemesterEntity getById(long semesterId);

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    public abstract LiveData<SemesterEntity> observeCurrentSemester();

    @Query("SELECT * FROM semesters WHERE isCurrent = 1 LIMIT 1")
    public abstract SemesterEntity getCurrentSemester();

    @Query("SELECT COUNT(*) FROM semesters WHERE isCurrent = 1")
    public abstract int countCurrentSemesters();

    @Query("SELECT EXISTS(SELECT 1 FROM semesters WHERE id = :semesterId)")
    protected abstract boolean existsById(long semesterId);

    @Query("UPDATE semesters SET isCurrent = CASE WHEN id = :semesterId THEN 1 ELSE 0 END")
    protected abstract int updateCurrentSemesterFlags(long semesterId);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract long insertRow(SemesterEntity semester);

    @Update
    protected abstract int updateRow(SemesterEntity semester);

    @Delete
    public abstract int delete(SemesterEntity semester);

    @Transaction
    @Query("SELECT * FROM semesters WHERE id = :semesterId LIMIT 1")
    public abstract LiveData<SemesterWithSubjects> observeWithSubjects(long semesterId);

    @Transaction
    public long insert(SemesterEntity semester) {
        long newId = insertRow(semester);
        if (semester.isCurrent) {
            updateCurrentSemesterFlags(newId);
        }
        return newId;
    }

    @Transaction
    public int update(SemesterEntity semester) {
        int updatedRows = updateRow(semester);
        if (updatedRows > 0 && semester.isCurrent) {
            updateCurrentSemesterFlags(semester.id);
        }
        return updatedRows;
    }

    /** Sets exactly one existing semester as current, or leaves state unchanged if the ID is missing. */
    @Transaction
    public boolean setCurrentSemester(long semesterId) {
        if (!existsById(semesterId)) {
            return false;
        }
        updateCurrentSemesterFlags(semesterId);
        return true;
    }
}
