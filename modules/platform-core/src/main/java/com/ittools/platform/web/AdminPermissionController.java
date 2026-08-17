package com.ittools.platform.web;

import com.ittools.platform.service.PermissionService;
import com.ittools.platform.service.dto.PermissionDtos.ModulePermissionCommand;
import com.ittools.platform.service.dto.PermissionDtos.PermissionView;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/admin")
public class AdminPermissionController {
    private final PermissionService svc;

    public AdminPermissionController(PermissionService s) {
        this.svc = s;
    }

    @GetMapping("/permissions")
    public ApiResponse<List<PermissionView>> list() {
        return ApiResponse.ok(svc.listPermissions());
    }

    @PutMapping("/module-permissions")
    public ApiResponse<Void> upsert(@RequestBody ModulePermissionCommand c) {
        svc.upsertModulePermission(c);
        return ApiResponse.ok(null);
    }
}
