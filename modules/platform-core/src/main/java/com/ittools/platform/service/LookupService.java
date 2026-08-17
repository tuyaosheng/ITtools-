package com.ittools.platform.service;

import com.ittools.platform.domain.Role;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * RULING 1 (binding, scoped to Task 6): AuthController needs a
 * permissionCodesForRole(Role) lookup to shape the /me and /login response
 * payload, but the full LookupService (real Permission/RolePermission
 * queries, other lookup methods, lookup endpoints) is Task 7's
 * responsibility. This is intentionally a minimal stub with exactly one
 * method so AuthController compiles and the auth endpoints work end-to-end
 * now; Task 7 fleshes out the real implementation.
 */
@Service
public class LookupService {
    public List<String> permissionCodesForRole(Role role) {
        // TODO(Task 7): return real permission codes for role
        return Collections.emptyList();
    }
}
