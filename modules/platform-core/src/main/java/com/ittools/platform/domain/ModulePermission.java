package com.ittools.platform.domain;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * Maps table module_permission (id BIGINT identity, scope VARCHAR,
 * scope_ref_id BIGINT nullable, permission_id BIGINT, enabled BOOLEAN).
 * scopeRefId/permissionId are kept plain Long fields (not @ManyToOne), same
 * convention as RolePermission, since PermissionService only reads/writes
 * scalar values and never navigates a relationship.
 *
 * A no-arg constructor is required because PermissionService uses
 * modulePerms.findByScopeAndScopeRefIdAndPermissionId(...)
 * .orElseGet(ModulePermission::new) then sets fields via setters.
 */
@Entity
@Table(name = "module_permission")
public class ModulePermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope")
    private String scope;

    @Column(name = "scope_ref_id")
    private Long scopeRefId;

    @Column(name = "permission_id")
    private Long permissionId;

    @Column(name = "enabled")
    private boolean enabled;

    public ModulePermission() {
    }

    public Long getId() { return id; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public Long getScopeRefId() { return scopeRefId; }
    public void setScopeRefId(Long scopeRefId) { this.scopeRefId = scopeRefId; }

    public Long getPermissionId() { return permissionId; }
    public void setPermissionId(Long permissionId) { this.permissionId = permissionId; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
