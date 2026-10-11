package com.ittools.lab.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "lab_workstation")
public class LabWorkstation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pc_name", nullable = false, unique = true)
    private String pcName;

    @Column(name = "ip_address", nullable = false, unique = true)
    private String ipAddress;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected LabWorkstation() {}

    public LabWorkstation(String pcName, String ipAddress) {
        this.pcName = pcName;
        this.ipAddress = ipAddress;
    }

    public Long getId() { return id; }
    public String getPcName() { return pcName; }
    public void setPcName(String v) { this.pcName = v; }
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String v) { this.ipAddress = v; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
