package com.ittools.grading.web;

import com.ittools.grading.service.GradingService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher/grading")
public class TeacherGradingController {
    private final GradingService svc;

    public TeacherGradingController(GradingService svc) {
        this.svc = svc;
    }

    @GetMapping("/catalog")
    public ApiResponse<List<Map<String, Object>>> catalog() {
        return ApiResponse.ok(svc.catalog());
    }

    @GetMapping("/pages/{pageId}/rules")
    public ApiResponse<List<Map<String, Object>>> rules(@PathVariable Long pageId) {
        return ApiResponse.ok(svc.rules(pageId));
    }

    @PutMapping("/pages/{pageId}/rules")
    public ApiResponse<List<Map<String, Object>>> saveRules(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveRules(CurrentUser.id(), pageId, Body.of(body).maps("rules")));
    }

    @GetMapping("/rubrics")
    public ApiResponse<List<Map<String, Object>>> rubrics() {
        return ApiResponse.ok(svc.rubrics(CurrentUser.id()));
    }

    @PostMapping("/rubrics")
    public ApiResponse<Long> createRubric(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.saveRubric(CurrentUser.id(), null, b.str("name"), b.bool("shared", false), b.maps("items")));
    }

    @PutMapping("/rubrics/{id}")
    public ApiResponse<Long> updateRubric(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.saveRubric(CurrentUser.id(), id, b.str("name"), b.bool("shared", false), b.maps("items")));
    }

    @DeleteMapping("/rubrics/{id}")
    public ApiResponse<Void> deleteRubric(@PathVariable Long id) {
        svc.deleteRubric(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }
}
