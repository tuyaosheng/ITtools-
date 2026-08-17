package com.ittools.platform.repository;

import com.ittools.platform.domain.ModulePermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModulePermissionRepository extends JpaRepository<ModulePermission, Long> {
    Optional<ModulePermission> findByScopeAndScopeRefIdAndPermissionId(String scope, Long scopeRefId, Long permissionId);
}
