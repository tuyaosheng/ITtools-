package com.ittools.platform.web;

import com.ittools.platform.service.LookupService;
import com.ittools.platform.service.dto.LookupDtos.ClassOption;
import com.ittools.platform.service.dto.LookupDtos.StudentOption;
import com.ittools.platform.service.dto.LookupDtos.YearOption;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Anonymous login-dropdown endpoints - /api/public/** is permitAll in
 * SecurityConfig.
 */
@RestController
@RequestMapping("/api/public")
public class PublicLookupController {
    private final LookupService lookups;

    public PublicLookupController(LookupService l) {
        this.lookups = l;
    }

    @GetMapping("/school-years")
    public ApiResponse<List<YearOption>> years() {
        return ApiResponse.ok(lookups.activeYears());
    }

    @GetMapping("/classes")
    public ApiResponse<List<ClassOption>> classes(@RequestParam Long yearId) {
        return ApiResponse.ok(lookups.classesOf(yearId));
    }

    @GetMapping("/students")
    public ApiResponse<List<StudentOption>> students(@RequestParam Long classId) {
        return ApiResponse.ok(lookups.studentsOf(classId));
    }
}
