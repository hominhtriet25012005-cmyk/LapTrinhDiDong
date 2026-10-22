package com.example.demo.model;

import java.io.Serializable;

public class SemesterItem implements Serializable {
    private String id;
    private String name;
    private String academicYear;
    private String startDate;
    private String endDate;
    private int subjectCount;
    private boolean isCurrent;
    private String note;

    public SemesterItem() {}

    public SemesterItem(String id, String name, String academicYear, String period, int subjectCount, boolean isCurrent) {
        this.id = id;
        this.name = name;
        this.academicYear = academicYear;
        if (period != null && period.contains("–")) {
            String[] parts = period.split("–");
            this.startDate = parts[0].trim();
            this.endDate = parts.length > 1 ? parts[1].trim() : "";
        } else if (period != null && period.contains("-")) {
            String[] parts = period.split("-");
            this.startDate = parts[0].trim();
            this.endDate = parts.length > 1 ? parts[1].trim() : "";
        } else {
            this.startDate = period;
            this.endDate = "";
        }
        this.subjectCount = subjectCount;
        this.isCurrent = isCurrent;
        this.note = "";
    }

    public SemesterItem(String id, String name, String academicYear, String startDate,
                        String endDate, int subjectCount, boolean isCurrent, String note) {
        this.id = id;
        this.name = name;
        this.academicYear = academicYear;
        this.startDate = startDate;
        this.endDate = endDate;
        this.subjectCount = subjectCount;
        this.isCurrent = isCurrent;
        this.note = note;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAcademicYear() { return academicYear; }
    public void setAcademicYear(String academicYear) { this.academicYear = academicYear; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public int getSubjectCount() { return subjectCount; }
    public void setSubjectCount(int subjectCount) { this.subjectCount = subjectCount; }

    public boolean isCurrent() { return isCurrent; }
    public void setCurrent(boolean current) { isCurrent = current; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getPeriod() {
        boolean hasStartDate = startDate != null && !startDate.trim().isEmpty();
        boolean hasEndDate = endDate != null && !endDate.trim().isEmpty();
        if (!hasStartDate && !hasEndDate) {
            return "Chưa thiết lập thời gian";
        }
        return (hasStartDate ? startDate : "—") + " – " + (hasEndDate ? endDate : "—");
    }
}
