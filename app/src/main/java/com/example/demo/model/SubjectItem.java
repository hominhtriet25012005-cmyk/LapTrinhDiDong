package com.example.demo.model;

import java.io.Serializable;

public class SubjectItem implements Serializable {
    private String id;
    private String code;
    private String name;
    private int credits;
    private String lecturer;
    private String semester;
    private String colorType; // "blue", "green", "amber", "violet"
    private String note;
    private String initials;

    public SubjectItem() {}

    public SubjectItem(String id, String code, String name, int credits,
                       String lecturer, String semester, String colorType,
                       String note, String initials) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.lecturer = lecturer;
        this.semester = semester;
        this.colorType = colorType;
        this.note = note;
        this.initials = initials;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public int getCredits() { return credits; }
    public void setCredits(int credits) { this.credits = credits; }

    public String getLecturer() { return lecturer; }
    public void setLecturer(String lecturer) { this.lecturer = lecturer; }

    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }

    public String getColorType() { return colorType; }
    public void setColorType(String colorType) { this.colorType = colorType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public String getInitials() { return initials; }
    public void setInitials(String initials) { this.initials = initials; }
}
