package com.ittools.platform.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key for {@link RolePermission} ({@code role} VARCHAR +
 * {@code permission_id} BIGINT), used via @IdClass since Java 8 has no
 * records and this project avoids @EmbeddedId's extra indirection for a
 * simple two-column key.
 */
public class RolePermissionId implements Serializable {
    private String role;
    private Long permissionId;

    public RolePermissionId() {}
    public RolePermissionId(String role, Long permissionId) {
        this.role = role; this.permissionId = permissionId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RolePermissionId)) return false;
        RolePermissionId that = (RolePermissionId) o;
        return Objects.equals(role, that.role) && Objects.equals(permissionId, that.permissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, permissionId);
    }
}
