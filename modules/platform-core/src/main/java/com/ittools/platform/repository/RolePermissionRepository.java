package com.ittools.platform.repository;

import com.ittools.platform.domain.RolePermission;
import com.ittools.platform.domain.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {
}
