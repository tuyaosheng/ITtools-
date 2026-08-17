package com.ittools.platform.web;

import com.ittools.platform.service.SchoolYearService;
import com.ittools.platform.service.dto.SchoolYearDtos.SchoolYearCommand;
import com.ittools.platform.service.dto.SchoolYearDtos.SchoolYearView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * URL protection (ADMIN-only) is already enforced by SecurityConfig's
 * .antMatchers("/api/admin/**").hasRole("ADMIN") rule, so no extra
 * @PreAuthorize is needed here.
 */
@RestController
@RequestMapping("/api/admin/school-years")
public class AdminSchoolYearController {
    private final SchoolYearService svc;

    public AdminSchoolYearController(SchoolYearService s) {
        this.svc = s;
    }

    @GetMapping
    public ApiResponse<List<SchoolYearView>> list() {
        return ApiResponse.ok(svc.list());
    }

    @PostMapping
    public ApiResponse<SchoolYearView> create(@RequestBody SchoolYearCommand c) {
        return ApiResponse.ok(svc.create(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<SchoolYearView> update(@PathVariable Long id, @RequestBody SchoolYearCommand c) {
        return ApiResponse.ok(svc.update(id, c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ApiResponse.ok(null);
    }
}
