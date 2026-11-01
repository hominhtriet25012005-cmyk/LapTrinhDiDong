package com.example.demo.data.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One-off cancellation, modification or makeup attached to a recurring schedule occurrence.
 * CANCEL removes the original occurrence; MODIFY may keep it in place or move it; MAKEUP
 * replaces the missed original occurrence with a date outside the normal weekly pattern.
 */
@Entity(
        tableName = "schedule_exceptions",
        foreignKeys = @ForeignKey(
                entity = ScheduleEntity.class,
                parentColumns = "id",
                childColumns = "scheduleId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"scheduleId", "originalDateEpochDay"}, unique = true),
                @Index(value = {"replacementDateEpochDay"})
        }
)
public class ScheduleExceptionEntity {
    public static final String TYPE_CANCEL = "CANCEL";
    public static final String TYPE_MODIFY = "MODIFY";
    public static final String TYPE_MAKEUP = "MAKEUP";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long scheduleId;
    public long originalDateEpochDay;
    @NonNull
    public String exceptionType = TYPE_CANCEL;
    public Long replacementDateEpochDay;
    public Integer startMinute;
    public Integer endMinute;
    /** Null inherits the recurring room; an empty string deliberately clears it. */
    public String room;
    public String note;

    public ScheduleExceptionEntity() {
    }
}
