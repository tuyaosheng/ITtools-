package com.ittools.platform.domain;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity @Table(name = "permission")
public class Permission {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    private String code;
    private String name;
    private String module;

    protected Permission() {}
    public Permission(String code, String name, String module) {
        this.code = code; this.name = name; this.module = module;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public void setCode(String v) { this.code = v; }
    public String getName() { return name; }
    public void setName(String v) { this.name = v; }
    public String getModule() { return module; }
    public void setModule(String v) { this.module = v; }
}
