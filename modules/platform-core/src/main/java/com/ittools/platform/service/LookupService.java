package com.ittools.platform.service;

import com.ittools.platform.domain.Role;
import com.ittools.platform.repository.KlassRepository;
import com.ittools.platform.repository.PermissionRepository;
import com.ittools.platform.repository.SchoolYearRepository;
import com.ittools.platform.repository.UserRepository;
import com.ittools.platform.service.dto.LookupDtos.ClassOption;
import com.ittools.platform.service.dto.LookupDtos.StudentOption;
import com.ittools.platform.service.dto.LookupDtos.YearOption;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Task 7: fleshes out the Task 6 stub (which had only
 * permissionCodesForRole(Role), hardcoded to an empty list, so AuthController
 * would compile before Permission/RolePermission existed). Now backed by the
 * real Permission/RolePermission tables via PermissionRepository, plus the
 * three lookup methods backing the anonymous login-dropdown endpoints.
 *
 * Java 8 note: .toList() (Java 16+) is not available, so
 * Collectors.toList() is used throughout.
 */
@Service
public class LookupService {
    private final SchoolYearRepository years;
    private final KlassRepository classes;
    private final UserRepository users;
    private final PermissionRepository perms;

    public LookupService(SchoolYearRepository y, KlassRepository c, UserRepository u, PermissionRepository p) {
        this.years = y; this.classes = c; this.users = u; this.perms = p;
    }

    public List<String> permissionCodesForRole(Role role) {
        return perms.codesForRole(role.name());
    }

    public List<YearOption> activeYears() {
        return years.findAll().stream()
                .filter(sy -> sy.isActive())
                .map(sy -> new YearOption(sy.getId(), sy.getYearCode(), sy.getLabel()))
                .collect(Collectors.toList());
    }

    public List<ClassOption> classesOf(Long yearId) {
        return classes.findBySchoolYear_IdOrderByDisplayOrderAscNameAsc(yearId).stream()
                .map(k -> new ClassOption(k.getId(), k.getName()))
                .collect(Collectors.toList());
    }

    public List<StudentOption> studentsOf(Long classId) {
        return users.findByKlass_Id(classId).stream()
                .filter(u -> u.getRole() == Role.STUDENT && !u.isGraduated())
                .map(u -> new StudentOption(u.getId(), u.getName()))
                .collect(Collectors.toList());
    }
}
