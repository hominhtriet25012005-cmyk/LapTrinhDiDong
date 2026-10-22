package com.example.demo.model;

import java.io.Serializable;

public class ScheduleItem implements Serializable {
    private String id;
    private String subjectCode;
    private String subjectName;
    private String startTime;
    private String endTime;
    private String room;
    private String lecturer;
    private String dayOfWeek;
    private String colorType; // "blue", "green", "amber", "violet"
    private String semester;
    private String note;
    private String appliedPeriod;

    public ScheduleItem() {}

    public ScheduleItem(String id, String subjectCode, String subjectName, String startTime,
                        String endTime, String room, String lecturer, String dayOfWeek,
                        String colorType, String semester, String note, String appliedPeriod) {
        this.id = id;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.room = room;
        this.lecturer = lecturer;
        this.dayOfWeek = dayOfWeek;
        this.colorType = colorType;
        this.semester = semester;
        this.note = note;
        this.appliedPeriod = appliedPeriod;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }

    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public String getRoom() { return room; }
    public void setRoom(String room) { this.room = room; }

    public String getLecturer() { return lecturer; }
    public void setLecturer(String lecturer) { this.lecturer = lecturer; }

    public String getDayOfWeek() { return dayOfWeek; }
    public void setDayOfWeek(String dayOfWeek) { this.dayOfWeek = dayOfWeek; }

    public String getColorType() { return colorType; }
    public void setColorType(String colorType) { this.colorType = colorType; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getAppliedPeriod() { return appliedPeriod; }
    public void setAppliedPeriod(String appliedPeriod) { this.appliedPeriod = appliedPeriod; }
}
