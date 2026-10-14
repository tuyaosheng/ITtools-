package com.ittools.homework.web;

import com.ittools.homework.service.OpPracticeService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/** 课后操作题与操作题报表。 */
@RestController
public class OpPracticeController {
    private final OpPracticeService svc;

    public OpPracticeController(OpPracticeService svc) {
        this.svc = svc;
    }

    @GetMapping("/api/student/op-practice")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.ok(svc.list(CurrentUser.id()));
    }

    @GetMapping("/api/student/op-practice/{pageId}")
    public ApiResponse<Map<String, Object>> view(@PathVariable Long pageId) {
        return ApiResponse.ok(svc.view(CurrentUser.id(), pageId));
    }

    @PostMapping("/api/student/op-practice/{pageId}")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long pageId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(svc.submit(CurrentUser.id(), pageId, file));
    }

    @GetMapping("/api/teacher/op-practice/pages")
    public ApiResponse<List<Map<String, Object>>> teacherPages() {
        return ApiResponse.ok(svc.teacherPages(CurrentUser.id()));
    }

    @GetMapping("/api/teacher/op-practice/pages/{pageId}")
    public ApiResponse<Map<String, Object>> report(@PathVariable Long pageId, @RequestParam Long classId) {
        return ApiResponse.ok(svc.practiceReport(CurrentUser.id(), pageId, classId));
    }

    @GetMapping("/api/teacher/homework/lessons/{lessonId}/pages/{pageId}/rule-stats")
    public ApiResponse<List<Map<String, Object>>> ruleStats(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.lessonRuleStats(CurrentUser.id(), lessonId, pageId));
    }
}
