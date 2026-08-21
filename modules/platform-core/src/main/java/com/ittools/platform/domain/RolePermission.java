package com.ittools.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.IdClass;
import javax.persistence.Table;

/**
 * Maps table role_permission (role VARCHAR, permission_id BIGINT), composite
 * PK (role, permission_id). permissionId is kept a plain Long (not a
 * @ManyToOne to Permission) so PermissionRepository#codesForRole's JPQL
 * entity join ("rp.permissionId = p.id") has a simple scalar property to
 * bind against.
 */
@Entity @Table(name = "role_permission") @IdClass(RolePermissionId.class)
public class RolePermission {
    @Id @Column(name = "role") private String role;
    @Id @Column(name = "permission_id") private Long permissionId;

    protected RolePermission() {}
    public RolePermission(String role, Long permissionId) {
        this.role = role; this.permissionId = permissionId;
    }

    public String getRole() { return role; }
    public void setRole(String v) { this.role = v; }
    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long v) { this.permissionId = v; }
}
