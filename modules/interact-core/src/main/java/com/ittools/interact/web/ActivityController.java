package com.ittools.interact.web;

import com.ittools.interact.service.ActivityService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 课堂调查、随机抽题、随机点名。 */
@RestController
public class ActivityController {
    private final ActivityService svc;

    public ActivityController(ActivityService svc) {
        this.svc = svc;
    }

    // ---- 教师：页面配置 ----
    @GetMapping("/api/teacher/activity/pages/{pageId}/config")
    public ApiResponse<Map<String, Object>> config(@PathVariable Long pageId) {
        return ApiResponse.ok(svc.teacherConfig(CurrentUser.id(), pageId));
    }

    @PutMapping("/api/teacher/activity/pages/{pageId}/survey")
    public ApiResponse<Map<String, Object>> saveSurvey(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveSurvey(CurrentUser.id(), pageId, body));
    }

    @PutMapping("/api/teacher/activity/pages/{pageId}/questions")
    public ApiResponse<Map<String, Object>> saveQuestions(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        Object q = body.get("questions");
        return ApiResponse.ok(svc.saveQuestions(CurrentUser.id(), pageId, q instanceof java.util.List ? (java.util.List<?>) q : null));
    }

    // ---- 教师：课堂中查看 ----
    @GetMapping("/api/teacher/activity/lessons/{lessonId}/pages/{pageId}/survey")
    public ApiResponse<Map<String, Object>> teacherSurvey(@PathVariable Long lessonId, @PathVariable Long pageId,
                                                          @RequestParam(defaultValue = "CLASS") String scope) {
        return ApiResponse.ok(svc.teacherSurvey(CurrentUser.id(), lessonId, pageId, scope));
    }

    @GetMapping("/api/teacher/activity/lessons/{lessonId}/pages/{pageId}/take")
    public ApiResponse<Map<String, Object>> teacherTake(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.teacherTake(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/teacher/activity/lessons/{lessonId}/roll-call")
    public ApiResponse<Map<String, Object>> rollCallOverview(@PathVariable Long lessonId) {
        return ApiResponse.ok(svc.rollCallOverview(CurrentUser.id(), lessonId));
    }

    @PostMapping("/api/teacher/activity/lessons/{lessonId}/roll-call")
    public ApiResponse<Map<String, Object>> rollCall(@PathVariable Long lessonId, @RequestParam(defaultValue = "false") boolean repeat) {
        return ApiResponse.ok(svc.rollCall(CurrentUser.id(), lessonId, repeat));
    }

    // ---- 学生 ----
    @GetMapping("/api/student/activity/lessons/{lessonId}/pages/{pageId}/survey")
    public ApiResponse<Map<String, Object>> studentSurvey(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentSurvey(CurrentUser.id(), lessonId, pageId));
    }

    @PutMapping("/api/student/activity/lessons/{lessonId}/pages/{pageId}/survey")
    public ApiResponse<Map<String, Object>> submitSurvey(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        Object a = body.get("answers");
        @SuppressWarnings("unchecked") Map<String, Object> answers = a instanceof Map ? (Map<String, Object>) a : null;
        svc.submitSurvey(CurrentUser.id(), lessonId, pageId, answers);
        return ApiResponse.ok(svc.studentSurvey(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/student/activity/lessons/{lessonId}/pages/{pageId}/take")
    public ApiResponse<Map<String, Object>> studentTake(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentTake(CurrentUser.id(), lessonId, pageId));
    }

    @PostMapping("/api/student/activity/lessons/{lessonId}/pages/{pageId}/take")
    public ApiResponse<Map<String, Object>> draw(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.draw(CurrentUser.id(), lessonId, pageId));
    }
}
