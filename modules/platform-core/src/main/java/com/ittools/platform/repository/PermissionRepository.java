package com.ittools.platform.repository;

import com.ittools.platform.domain.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, Long> {
    // Ad-hoc JPQL entity join with ON (rp.permissionId = p.id), supported by
    // Hibernate 5.6 (Spring Boot 2.7's bundled version), joining two
    // otherwise-unrelated entities to look up the permission codes granted
    // to a role via role_permission.
    @Query("select p.code from Permission p join RolePermission rp on rp.permissionId = p.id where rp.role = :role")
    List<String> codesForRole(@Param("role") String role);
}
