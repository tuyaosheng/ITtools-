package com.ittools.quiz.web;

import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.quiz.service.PracticeService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 课后刷题：指定题库、抽题练习、刷题记录，以及教师端刷题情况、错误率、查重。 */
@RestController
public class PracticeController {
    private final PracticeService practice;

    public PracticeController(PracticeService practice) {
        this.practice = practice;
    }

    // ---- 教师 ----
    @GetMapping("/api/teacher/quiz/practice-sets")
    public ApiResponse<List<Map<String, Object>>> sets() {
        return ApiResponse.ok(practice.sets(CurrentUser.id()));
    }

    @PostMapping("/api/teacher/quiz/practice-sets")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(practice.saveSet(CurrentUser.id(), null, b.str("name"), b.lng("paperId"), b.longs("classIds"), b.bool("enabled", true)));
    }

    @PutMapping("/api/teacher/quiz/practice-sets/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(practice.saveSet(CurrentUser.id(), id, b.str("name"), b.lng("paperId"), b.longs("classIds"), b.bool("enabled", true)));
    }

    @DeleteMapping("/api/teacher/quiz/practice-sets/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        practice.deleteSet(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/quiz/tests/{id}/practice-set")
    public ApiResponse<Map<String, Object>> fromTest(@PathVariable Long id) {
        return ApiResponse.ok(practice.setFromTest(CurrentUser.id(), id));
    }

    @GetMapping("/api/teacher/quiz/practice-report")
    public ApiResponse<List<Map<String, Object>>> classReport(@RequestParam Long classId) {
        return ApiResponse.ok(practice.classReport(CurrentUser.id(), classId));
    }

    @GetMapping("/api/teacher/quiz/error-rates")
    public ApiResponse<List<Map<String, Object>>> errorRates(@RequestParam(defaultValue = "5") int minCount) {
        return ApiResponse.ok(practice.errorRates(CurrentUser.id(), minCount));
    }

    @GetMapping("/api/teacher/quiz/duplicates")
    public ApiResponse<List<Map<String, Object>>> duplicates() {
        return ApiResponse.ok(practice.duplicates(CurrentUser.id()));
    }

    // ---- 学生 ----
    @GetMapping("/api/student/quiz/practice-sets")
    public ApiResponse<List<Map<String, Object>>> available() {
        return ApiResponse.ok(practice.availableSets(CurrentUser.id()));
    }

    @PostMapping("/api/student/quiz/practice-sessions")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(practice.start(CurrentUser.id(), b.reqStr("source", "刷题方式"), b.lng("setId"), b.intv("count", 10), b.str("category")));
    }

    @GetMapping("/api/student/quiz/practice-sessions")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(practice.mySessions(CurrentUser.id()));
    }

    @GetMapping("/api/student/quiz/practice-sessions/{id}")
    public ApiResponse<Map<String, Object>> session(@PathVariable Long id) {
        return ApiResponse.ok(practice.session(CurrentUser.id(), id));
    }

    @PostMapping("/api/student/quiz/practice-sessions/{id}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(practice.submit(CurrentUser.id(), id, Body.of(body).maps("answers")));
    }
}
