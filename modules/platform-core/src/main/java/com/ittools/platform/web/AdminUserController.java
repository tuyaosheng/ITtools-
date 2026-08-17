package com.ittools.platform.web;

import com.ittools.platform.service.UserService;
import com.ittools.platform.service.dto.UserDtos.CreateUserCommand;
import com.ittools.platform.service.dto.UserDtos.ResetPasswordCommand;
import com.ittools.platform.service.dto.UserDtos.UpdateUserCommand;
import com.ittools.platform.service.dto.UserDtos.UserView;
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
 * @PreAuthorize is needed here (mirrors AdminSchoolYearController /
 * AdminKlassController, Tasks 8/9).
 */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final UserService svc;

    public AdminUserController(UserService s) {
        this.svc = s;
    }

    @GetMapping
    public ApiResponse<List<UserView>> list(@RequestParam(required = false) String role,
                                             @RequestParam(required = false) Long classId) {
        return ApiResponse.ok(svc.list(role, classId));
    }

    @PostMapping
    public ApiResponse<UserView> create(@RequestBody CreateUserCommand c) {
        return ApiResponse.ok(svc.create(c));
    }

    @PutMapping("/{id}")
    public ApiResponse<UserView> update(@PathVariable Long id, @RequestBody UpdateUserCommand c) {
        return ApiResponse.ok(svc.update(id, c));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long id, @RequestBody ResetPasswordCommand c) {
        svc.resetPassword(id, c);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/graduate")
    public ApiResponse<Void> graduate(@PathVariable Long id, @RequestParam boolean value) {
        svc.setGraduated(id, value);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/enabled")
    public ApiResponse<Void> enabled(@PathVariable Long id, @RequestParam boolean value) {
        svc.setEnabled(id, value);
        return ApiResponse.ok(null);
    }
}
