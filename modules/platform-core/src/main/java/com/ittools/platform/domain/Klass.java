package com.ittools.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;

@Entity @Table(name = "klass")
public class Klass {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "school_year_id", nullable = false)
    private SchoolYear schoolYear;
    private String name;
    @Column(name = "display_order") private int displayOrder;

    protected Klass() {}
    public Klass(SchoolYear y, String name, int order) { this.schoolYear = y; this.name = name; this.displayOrder = order; }
    public Long getId() { return id; }
    public SchoolYear getSchoolYear() { return schoolYear; }
    public void setSchoolYear(SchoolYear v) { this.schoolYear = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int v) { this.displayOrder = v; }
}
