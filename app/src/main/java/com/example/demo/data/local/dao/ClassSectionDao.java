package com.example.demo.data.local.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.example.demo.data.local.entity.ClassSectionEntity;
import com.example.demo.data.local.relation.ClassSectionWithSchedules;

import java.util.List;

@Dao
public interface ClassSectionDao {
    @Query("SELECT * FROM class_sections ORDER BY classCode COLLATE NOCASE, id")
    List<ClassSectionEntity> getAll();

    @Query("SELECT * FROM class_sections WHERE subjectId = :subjectId ORDER BY classCode COLLATE NOCASE, id")
    LiveData<List<ClassSectionEntity>> observeForSubject(long subjectId);

    @Query("SELECT * FROM class_sections WHERE id = :classSectionId LIMIT 1")
    LiveData<ClassSectionEntity> observeById(long classSectionId);

    @Query("SELECT * FROM class_sections WHERE id = :classSectionId LIMIT 1")
    ClassSectionEntity getById(long classSectionId);

    @Query("SELECT * FROM class_sections WHERE subjectId = :subjectId AND classCode = :classCode COLLATE NOCASE ORDER BY id LIMIT 1")
    ClassSectionEntity findBySubjectAndCode(long subjectId, String classCode);

    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(ClassSectionEntity classSection);

    @Update
    int update(ClassSectionEntity classSection);

    @Delete
    int delete(ClassSectionEntity classSection);

    @Transaction
    @Query("SELECT * FROM class_sections WHERE id = :classSectionId LIMIT 1")
    LiveData<ClassSectionWithSchedules> observeWithSchedules(long classSectionId);
}
