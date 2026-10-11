package com.ittools.grading.python;

import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
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

@RestController
public class PythonController {
    private final PythonService svc;

    public PythonController(PythonService svc) {
        this.svc = svc;
    }

    // ================= 教师：题库 =================
    @GetMapping("/api/teacher/python/problems")
    public ApiResponse<List<Map<String, Object>>> problems(@RequestParam(defaultValue = "false") boolean shared,
                                                           @RequestParam(required = false) String keyword) {
        return ApiResponse.ok(svc.problems(CurrentUser.id(), shared, keyword));
    }

    @GetMapping("/api/teacher/python/problems/{id}")
    public ApiResponse<Map<String, Object>> problem(@PathVariable Long id) {
        return ApiResponse.ok(svc.problem(CurrentUser.id(), id));
    }

    @PostMapping("/api/teacher/python/problems")
    public ApiResponse<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveProblem(CurrentUser.id(), null, body));
    }

    @PutMapping("/api/teacher/python/problems/{id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveProblem(CurrentUser.id(), id, body));
    }

    @PostMapping("/api/teacher/python/problems/{id}/copy")
    public ApiResponse<Long> copy(@PathVariable Long id) {
        return ApiResponse.ok(svc.copyProblem(CurrentUser.id(), id));
    }

    @DeleteMapping("/api/teacher/python/problems/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        svc.deleteProblem(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    /** 教师在服务端沙箱试运行代码（如用参考代码生成期望输出）。 */
    @PostMapping("/api/teacher/python/run")
    public ApiResponse<List<Map<String, Object>>> run(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.teacherRun(code(body), b.strs("inputs"), b.intv("timeLimitMs")));
    }

    // ================= 教师：课堂 =================
    @GetMapping("/api/teacher/python/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> lessonPanel(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.lessonPanel(CurrentUser.id(), lessonId, pageId));
    }

    @PutMapping("/api/teacher/python/lessons/{lessonId}/pages/{pageId}/reference")
    public ApiResponse<Void> reference(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        svc.setShowReference(CurrentUser.id(), lessonId, pageId, Body.of(body).bool("show", false));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/python/lessons/{lessonId}/pages/{pageId}/regrade")
    public ApiResponse<Integer> regrade(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.regradeLessonPage(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/teacher/python/submissions/{id}")
    public ApiResponse<Map<String, Object>> submission(@PathVariable Long id) {
        return ApiResponse.ok(svc.submission(CurrentUser.id(), id));
    }

    // ================= 学生：课堂 =================
    @GetMapping("/api/student/python/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> studentView(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentLessonView(CurrentUser.id(), lessonId, pageId));
    }

    @PutMapping("/api/student/python/lessons/{lessonId}/pages/{pageId}/code")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.studentLessonSave(CurrentUser.id(), lessonId, pageId, code(body)));
    }

    @PostMapping("/api/student/python/lessons/{lessonId}/pages/{pageId}/submit")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.studentLessonSubmit(CurrentUser.id(), lessonId, pageId, code(body)));
    }

    // ================= 学生：课后题库 =================
    @GetMapping("/api/student/python/practice")
    public ApiResponse<List<Map<String, Object>>> practice() {
        return ApiResponse.ok(svc.practiceList(CurrentUser.id()));
    }

    @GetMapping("/api/student/python/practice/{id}")
    public ApiResponse<Map<String, Object>> practiceView(@PathVariable Long id) {
        return ApiResponse.ok(svc.practiceView(CurrentUser.id(), id));
    }

    @PutMapping("/api/student/python/practice/{id}/code")
    public ApiResponse<Map<String, Object>> practiceSave(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.practiceSave(CurrentUser.id(), id, code(body)));
    }

    @PostMapping("/api/student/python/practice/{id}/submit")
    public ApiResponse<Map<String, Object>> practiceSubmit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.practiceSubmit(CurrentUser.id(), id, code(body)));
    }

    /** 代码原样保存（Body.str 会 trim，可能破坏首行缩进或改变内容）。 */
    private static String code(Map<String, Object> body) {
        Object v = body.get("code");
        return v == null ? null : String.valueOf(v);
    }
}
