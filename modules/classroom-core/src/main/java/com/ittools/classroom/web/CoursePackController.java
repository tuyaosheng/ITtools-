package com.ittools.classroom.web;

import com.ittools.classroom.service.CoursePackService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 课程包导出（下载 zip）与导入（上传 zip 生成新课程）。 */
@RestController
public class CoursePackController {
    private final CoursePackService packs;

    public CoursePackController(CoursePackService packs) {
        this.packs = packs;
    }

    @GetMapping("/api/teacher/courses/{id}/pack")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable Long id) {
        Long me = CurrentUser.id();
        String name = packs.fileName(me, id);
        StreamingResponseBody body = out -> packs.export(me, id, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping("/api/teacher/courses/import-pack")
    public ApiResponse<Map<String, Object>> importPack(@RequestParam("file") MultipartFile file, @RequestParam(required = false) Long termId) {
        return ApiResponse.ok(packs.importPack(CurrentUser.id(), file, termId));
    }
}
