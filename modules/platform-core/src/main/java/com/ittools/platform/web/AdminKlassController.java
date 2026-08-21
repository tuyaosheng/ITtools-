package com.ittools.platform.web;

import com.ittools.platform.service.KlassService;
import com.ittools.platform.service.dto.KlassDtos.KlassCommand;
import com.ittools.platform.service.dto.KlassDtos.KlassView;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * URL protection (ADMIN-only) is already enforced by SecurityConfig's
 * .antMatchers("/api/admin/**").hasRole("ADMIN") rule, so no extra
 * @PreAuthorize is needed here (mirrors AdminSchoolYearController, Task 8).
 */
@RestController
@RequestMapping("/api/admin/classes")
public class AdminKlassController {
    private final KlassService svc;

    public AdminKlassController(KlassService s) {
        this.svc = s;
    }

    @GetMapping
    public ApiResponse<List<KlassView>> list(@RequestParam Long yearId) {
        return ApiResponse.ok(svc.listByYear(yearId));
    }

    @PostMapping
    public ApiResponse<KlassView> create(@RequestBody KlassCommand c) {
        return ApiResponse.ok(svc.create(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<KlassView> update(@PathVariable Long id, @RequestBody KlassCommand c) {
        return ApiResponse.ok(svc.update(id, c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ApiResponse.ok(null);
    }
}
