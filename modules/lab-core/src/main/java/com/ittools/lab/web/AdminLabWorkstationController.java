package com.ittools.lab.web;

import com.ittools.lab.service.LabWorkstationService;
import com.ittools.lab.service.dto.LabWorkstationDtos.LabWorkstationCommand;
import com.ittools.lab.service.dto.LabWorkstationDtos.LabWorkstationView;
import com.ittools.platform.web.ApiResponse;
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
 * ADMIN-only 保护由 SecurityConfig 的 .antMatchers("/api/admin/**").hasRole("ADMIN")
 * 统一覆盖，此处无需额外 @PreAuthorize（与 AdminKlassController 等一致）。
 */
@RestController
@RequestMapping("/api/admin/lab-workstations")
public class AdminLabWorkstationController {
    private final LabWorkstationService svc;

    public AdminLabWorkstationController(LabWorkstationService s) {
        this.svc = s;
    }

    @GetMapping
    public ApiResponse<List<LabWorkstationView>> list() {
        return ApiResponse.ok(svc.list());
    }

    @PostMapping
    public ApiResponse<LabWorkstationView> create(@RequestBody LabWorkstationCommand c) {
        return ApiResponse.ok(svc.create(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<LabWorkstationView> update(@PathVariable Long id, @RequestBody LabWorkstationCommand c) {
        return ApiResponse.ok(svc.update(id, c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ApiResponse.ok(null);
    }
}
