package com.ittools.homework.web;

import com.ittools.homework.service.LessonScoreService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/** 一堂课的组合成绩（测验 + 作品 + Python + 课堂表现）。 */
@RestController
public class LessonScoreController {
    private final LessonScoreService scores;

    public LessonScoreController(LessonScoreService scores) {
        this.scores = scores;
    }

    @GetMapping("/api/teacher/lessons/{lessonId}/scores")
    public ApiResponse<Map<String, Object>> scores(@PathVariable Long lessonId) {
        return ApiResponse.ok(scores.scores(CurrentUser.id(), lessonId));
    }

    @GetMapping("/api/teacher/lessons/{lessonId}/seat-flags")
    public ApiResponse<java.util.List<Map<String, Object>>> seatFlags(@PathVariable Long lessonId) {
        return ApiResponse.ok(scores.seatFlags(CurrentUser.id(), lessonId));
    }

    @GetMapping("/api/teacher/lessons/{lessonId}/scores/export")
    public ResponseEntity<byte[]> export(@PathVariable Long lessonId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("课堂成绩_" + lessonId + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(scores.export(CurrentUser.id(), lessonId));
    }
}
