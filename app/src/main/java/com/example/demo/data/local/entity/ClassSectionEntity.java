package com.example.demo.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "class_sections",
        foreignKeys = @ForeignKey(
                entity = SubjectEntity.class,
                parentColumns = "id",
                childColumns = "subjectId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index(value = {"subjectId"})}
)
public class ClassSectionEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long subjectId;
    public String classCode;
    public String lecturer;
    public String note;

    public ClassSectionEntity() {
    }

    @Ignore
    public ClassSectionEntity(long subjectId, String classCode, String lecturer, String note) {
        this.subjectId = subjectId;
        this.classCode = classCode;
        this.lecturer = lecturer;
        this.note = note;
    }
}
