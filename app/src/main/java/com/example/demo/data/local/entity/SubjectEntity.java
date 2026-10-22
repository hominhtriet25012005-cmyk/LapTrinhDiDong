package com.example.demo.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "subjects",
        foreignKeys = @ForeignKey(
                entity = SemesterEntity.class,
                parentColumns = "id",
                childColumns = "semesterId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"semesterId", "code"}, unique = true)
        }
)
public class SubjectEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long semesterId;
    public String code;
    public String name;
    public int credits;
    public String defaultLecturer;
    public String colorKey;
    public String note;

    public SubjectEntity() {
    }

    @Ignore
    public SubjectEntity(long semesterId, String code, String name, int credits,
                         String defaultLecturer, String colorKey, String note) {
        this.semesterId = semesterId;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.defaultLecturer = defaultLecturer;
        this.colorKey = colorKey;
        this.note = note;
    }
}
