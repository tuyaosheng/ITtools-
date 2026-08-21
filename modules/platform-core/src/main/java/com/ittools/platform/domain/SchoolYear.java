package com.ittools.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity @Table(name = "school_year")
public class SchoolYear {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "year_code", nullable = false, unique = true) private String yearCode;
    private String label;
    private boolean active = true;

    protected SchoolYear() {}
    public SchoolYear(String yearCode, String label, boolean active) {
        this.yearCode = yearCode; this.label = label; this.active = active;
    }
    public Long getId() { return id; }
    public String getYearCode() { return yearCode; }
    public void setYearCode(String v) { this.yearCode = v; }
    public String getLabel() { return label; }
    public void setLabel(String v) { this.label = v; }
    public boolean isActive() { return active; }
    public void setActive(boolean v) { this.active = v; }
}
