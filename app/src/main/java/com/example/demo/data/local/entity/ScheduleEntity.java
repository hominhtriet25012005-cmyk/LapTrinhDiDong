package com.example.demo.data.local.entity;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "schedules",
        foreignKeys = @ForeignKey(
                entity = ClassSectionEntity.class,
                parentColumns = "id",
                childColumns = "classSectionId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {
                @Index(value = {"classSectionId"}),
                @Index(value = {"dayOfWeek", "startMinute"})
        }
)
public class ScheduleEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long classSectionId;
    /** ISO-8601 weekday: Monday = 1 through Sunday = 7. */
    public int dayOfWeek;
    /** Minutes after midnight. */
    public int startMinute;
    /** Minutes after midnight; 1440 represents midnight at the end of the day. */
    public int endMinute;
    public String room;
    public long validFromEpochDay;
    public long validToEpochDay;
    public String note;
    public boolean notificationEnabled;
    public int reminderMinutesBefore;

    public ScheduleEntity() {
    }

    @Ignore
    public ScheduleEntity(long classSectionId, int dayOfWeek, int startMinute, int endMinute,
                          String room, long validFromEpochDay, long validToEpochDay, String note) {
        this.classSectionId = classSectionId;
        this.dayOfWeek = dayOfWeek;
        this.startMinute = startMinute;
        this.endMinute = endMinute;
        this.room = room;
        this.validFromEpochDay = validFromEpochDay;
        this.validToEpochDay = validToEpochDay;
        this.note = note;
        this.notificationEnabled = false;
        this.reminderMinutesBefore = 1_440;
    }
}
