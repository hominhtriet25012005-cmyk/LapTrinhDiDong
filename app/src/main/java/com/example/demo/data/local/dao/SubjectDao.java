package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.demo.data.local.entity.SubjectEntity;
import com.example.demo.data.local.relation.SubjectWithClassSections;

import java.util.List;

@Dao
public interface SubjectDao {
    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    LiveData<List<SubjectEntity>> observeAll();

    @Query("SELECT * FROM subjects ORDER BY name COLLATE NOCASE")
    List<SubjectEntity> getAll();

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId ORDER BY name COLLATE NOCASE")
    LiveData<List<SubjectEntity>> observeForSemester(long semesterId);

    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    LiveData<SubjectEntity> observeById(long subjectId);

    @Query("SELECT * FROM subjects WHERE semesterId = :semesterId AND (code LIKE '%' || :query || '%' COLLATE NOCASE OR name LIKE '%' || :query || '%' COLLATE NOCASE) ORDER BY name COLLATE NOCASE")
    LiveData<List<SubjectEntity>> search(long semesterId, String query);

    @Query("SELECT * FROM subjects WHERE code LIKE '%' || :query || '%' COLLATE NOCASE OR name LIKE '%' || :query || '%' COLLATE NOCASE ORDER BY name COLLATE NOCASE")
    LiveData<List<SubjectEntity>> searchAll(String query);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(SubjectEntity subject);

    @Update
    int update(SubjectEntity subject);

    @Delete
    int delete(SubjectEntity subject);

    @Transaction
    @Query("SELECT * FROM subjects WHERE id = :subjectId LIMIT 1")
    LiveData<SubjectWithClassSections> observeWithClassSections(long subjectId);
}
