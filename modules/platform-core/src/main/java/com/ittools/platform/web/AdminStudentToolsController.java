package com.ittools.platform.web;

import com.ittools.platform.security.SsoService;
import com.ittools.platform.service.BatchEditService;
import com.ittools.platform.settings.SystemSettingService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** 管理员：用表格批量修改学生信息；生成第三方登录密钥。 */
@RestController
public class AdminStudentToolsController {
    private final BatchEditService batch;
    private final SystemSettingService settings;

    public AdminStudentToolsController(BatchEditService batch, SystemSettingService settings) {
        this.batch = batch;
        this.settings = settings;
    }

    @GetMapping("/api/admin/batch-edit/students/export")
    public ResponseEntity<byte[]> export(@RequestParam Long yearId) throws IOException {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("学生信息表.xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(batch.export(yearId));
    }

    @PostMapping("/api/admin/batch-edit/students/preview")
    public ApiResponse<Map<String, Object>> preview(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(batch.preview(file));
    }

    @PostMapping("/api/admin/batch-edit/students/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(batch.apply(file));
    }

    /** 生成新的第三方登录密钥（旧密钥立即失效）。 */
    @PostMapping("/api/admin/settings/sso-secret")
    public ApiResponse<Map<String, String>> newSecret() {
        String secret = SsoService.newSecret();
        settings.update(Collections.singletonMap(SystemSettingService.SSO_SECRET, secret));
        Map<String, String> m = new LinkedHashMap<>();
        m.put("secret", secret);
        return ApiResponse.ok(m);
    }
}
