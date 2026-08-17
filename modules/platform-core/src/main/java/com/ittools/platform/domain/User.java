package com.ittools.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.time.OffsetDateTime;

@Entity @Table(name = "users")
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Role role;
    private String name;
    @Column(name = "login_name") private String loginName;
    @Column(name = "student_no") private String studentNo;
    private String xjh;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "class_id") private Klass klass;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "enroll_year_id") private SchoolYear enrollYear;
    private boolean graduated = false;
    private boolean enabled = true;
    @Column(name = "created_at", updatable = false, insertable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", insertable = false) private OffsetDateTime updatedAt;

    public Long getId() { return id; }
    public Role getRole() { return role; } public void setRole(Role v) { this.role = v; }
    public String getName() { return name; } public void setName(String v) { this.name = v; }
    public String getLoginName() { return loginName; } public void setLoginName(String v) { this.loginName = v; }
    public String getStudentNo() { return studentNo; } public void setStudentNo(String v) { this.studentNo = v; }
    public String getXjh() { return xjh; } public void setXjh(String v) { this.xjh = v; }
    public String getPasswordHash() { return passwordHash; } public void setPasswordHash(String v) { this.passwordHash = v; }
    public Klass getKlass() { return klass; } public void setKlass(Klass v) { this.klass = v; }
    public SchoolYear getEnrollYear() { return enrollYear; } public void setEnrollYear(SchoolYear v) { this.enrollYear = v; }
    public boolean isGraduated() { return graduated; } public void setGraduated(boolean v) { this.graduated = v; }
    public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { this.enabled = v; }
}
