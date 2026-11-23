package com.ittools.platform.settings;

import com.ittools.platform.web.ApiResponse;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
public class FeatureController {
    private final FeatureService svc;

    public FeatureController(FeatureService svc) {
        this.svc = svc;
    }

    /** 当前登录者所在角色被管理员关闭的功能（前端据此隐藏菜单）。 */
    @GetMapping("/api/features/mine")
    public ApiResponse<Map<String, List<String>>> mine(Authentication auth) {
        List<String> off = new ArrayList<>();
        if (auth != null) {
            if (hasRole(auth, "STUDENT")) off.addAll(svc.disabled(FeatureCatalog.STUDENT));
            else if (hasRole(auth, "TEACHER")) off.addAll(svc.disabled(FeatureCatalog.TEACHER));
        }
        return ApiResponse.ok(Collections.singletonMap("disabled", off));
    }

    @GetMapping("/api/admin/features")
    public ApiResponse<Map<String, Object>> overview() {
        return ApiResponse.ok(svc.overview());
    }

    @PutMapping("/api/admin/features")
    public ApiResponse<Map<String, Object>> save(@RequestBody Map<String, List<String>> body) {
        return ApiResponse.ok(svc.save(body));
    }

    private static boolean hasRole(Authentication a, String r) {
        return a.getAuthorities().stream().anyMatch(x -> ("ROLE_" + r).equals(x.getAuthority()));
    }
}
