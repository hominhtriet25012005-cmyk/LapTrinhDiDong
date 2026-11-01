package com.example.demo.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One exception to a weekly schedule: a cancelled occurrence, a modified occurrence,
 * or a standalone makeup session (scheduleId and originalEpochDay are null for makeups).
 */
@Entity(
        tableName = "schedule_exceptions",
        foreignKeys = {
                @ForeignKey(
                        entity = ScheduleEntity.class,
                        parentColumns = "id",
                        childColumns = "scheduleId",
                        onDelete = ForeignKey.CASCADE
                ),
                @ForeignKey(
                        entity = ClassSectionEntity.class,
                        parentColumns = "id",
                        childColumns = "classSectionId",
                        onDelete = ForeignKey.CASCADE
                )
        },
        indices = {
                @Index(value = {"scheduleId", "originalEpochDay"}, unique = true),
                @Index(value = {"classSectionId"}),
                @Index(value = {"newEpochDay"})
        }
)
public class ScheduleExceptionEntity {
    public static final String TYPE_CANCEL = "CANCEL";
    public static final String TYPE_MODIFY = "MODIFY";
    public static final String TYPE_MAKEUP = "MAKEUP";

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String type;
    public Long scheduleId;
    public long classSectionId;
    public Long originalEpochDay;
    public Long newEpochDay;
    public Integer newStartMinute;
    public Integer newEndMinute;
    public String newRoom;
    public String note;
    public long createdAtMillis;

    public ScheduleExceptionEntity() {
    }

    public static ScheduleExceptionEntity cancel(long scheduleId, long classSectionId,
                                                 long originalEpochDay, String note) {
        ScheduleExceptionEntity entity = new ScheduleExceptionEntity();
        entity.type = TYPE_CANCEL;
        entity.scheduleId = scheduleId;
        entity.classSectionId = classSectionId;
        entity.originalEpochDay = originalEpochDay;
        entity.note = note;
        return entity;
    }

    /** Any of newEpochDay, newStartMinute/newEndMinute (together) or newRoom may be null. */
    public static ScheduleExceptionEntity modify(long scheduleId, long classSectionId,
                                                 long originalEpochDay, Long newEpochDay,
                                                 Integer newStartMinute, Integer newEndMinute,
                                                 String newRoom, String note) {
        ScheduleExceptionEntity entity = new ScheduleExceptionEntity();
        entity.type = TYPE_MODIFY;
        entity.scheduleId = scheduleId;
        entity.classSectionId = classSectionId;
        entity.originalEpochDay = originalEpochDay;
        entity.newEpochDay = newEpochDay;
        entity.newStartMinute = newStartMinute;
        entity.newEndMinute = newEndMinute;
        entity.newRoom = newRoom;
        entity.note = note;
        return entity;
    }

    public static ScheduleExceptionEntity makeup(long classSectionId, long newEpochDay,
                                                 int newStartMinute, int newEndMinute,
                                                 String newRoom, String note) {
        ScheduleExceptionEntity entity = new ScheduleExceptionEntity();
        entity.type = TYPE_MAKEUP;
        entity.classSectionId = classSectionId;
        entity.newEpochDay = newEpochDay;
        entity.newStartMinute = newStartMinute;
        entity.newEndMinute = newEndMinute;
        entity.newRoom = newRoom;
        entity.note = note;
        return entity;
    }
}