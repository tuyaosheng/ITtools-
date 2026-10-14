package com.ittools.platform.settings;

import com.ittools.platform.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
public class SettingsController {
    private final SystemSettingService svc;

    public SettingsController(SystemSettingService svc) {
        this.svc = svc;
    }

    @GetMapping("/api/admin/settings")
    public ApiResponse<Map<String, String>> all() {
        return ApiResponse.ok(svc.all());
    }

    @PutMapping("/api/admin/settings")
    public ApiResponse<Map<String, String>> update(@RequestBody Map<String, String> changes) {
        return ApiResponse.ok(svc.update(changes));
    }

    /** 登录页等免登录可见的参数（学校名称、工作模式）。 */
    @GetMapping("/api/public/settings")
    public ApiResponse<Map<String, String>> publicSettings() {
        Map<String, String> m = new HashMap<>();
        m.put("schoolName", svc.get(SystemSettingService.SCHOOL_NAME));
        m.put("loginMode", svc.get(SystemSettingService.LOGIN_MODE));
        m.put("notice", svc.get(SystemSettingService.SITE_NOTICE));
        return ApiResponse.ok(m);
    }
}
