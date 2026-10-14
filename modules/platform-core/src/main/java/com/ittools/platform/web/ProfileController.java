package com.ittools.platform.web;

import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.service.ProfileService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 个人资料、头像（本人）与管理员批量导入头像。 */
@RestController
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/api/account/profile")
    public ApiResponse<Map<String, Object>> profile() {
        return ApiResponse.ok(profiles.profile(CurrentUser.id()));
    }

    @PutMapping("/api/account/profile")
    public ApiResponse<Map<String, Object>> update(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(profiles.update(CurrentUser.id(), body));
    }

    @PostMapping("/api/account/avatar")
    public ApiResponse<Map<String, Object>> avatar(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(profiles.uploadOwn(CurrentUser.id(), file));
    }

    @DeleteMapping("/api/account/avatar")
    public ApiResponse<Map<String, Object>> removeAvatar() {
        if (!Boolean.TRUE.equals(profiles.profile(CurrentUser.id()).get("canChangeAvatar"))) throw new IllegalArgumentException("系统未开放自行修改头像");
        profiles.removeAvatar(CurrentUser.id());
        return ApiResponse.ok(profiles.profile(CurrentUser.id()));
    }

    /** 任一登录用户可查看头像（课堂座位图、点名等处显示）。 */
    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<Resource> avatarOf(@PathVariable Long id) {
        Path f = profiles.avatarFile(id);
        if (f == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                .body(new FileSystemResource(f));
    }

    @PostMapping("/api/admin/avatars/import")
    public ApiResponse<Map<String, Object>> importZip(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(profiles.importZip(file));
    }

    @PostMapping("/api/admin/users/{id}/avatar")
    public ApiResponse<Void> adminSet(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        profiles.setAvatar(id, file.getBytes());
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/users/{id}/avatar")
    public ApiResponse<Void> adminRemove(@PathVariable Long id) {
        profiles.removeAvatar(id);
        return ApiResponse.ok(null);
    }
}
