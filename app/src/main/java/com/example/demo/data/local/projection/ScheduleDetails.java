package com.example.demo.data.local.projection;

public class ScheduleDetails {
    public long scheduleId;
    public long classSectionId;
    public int dayOfWeek;
    public int startMinute;
    public int endMinute;
    public String room;
    public long validFromEpochDay;
    public long validToEpochDay;
    public String note;
    public boolean notificationEnabled;
    public int reminderMinutesBefore;

    public String classCode;
    public String lecturer;
    public long subjectId;
    public String subjectCode;
    public String subjectName;
    public String subjectColorKey;
    public long semesterId;
    public String semesterName;
    public String semesterAcademicYear;
}
