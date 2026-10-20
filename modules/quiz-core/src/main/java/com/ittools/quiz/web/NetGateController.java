package com.ittools.quiz.web;

import com.ittools.classroom.service.LessonService;
import com.ittools.classroom.service.NetGateService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.quiz.service.TestService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 成绩开网：课堂里按某次测验的成绩（达标才开网、限定机会次数）自动控制学生机网络。 */
@RestController
public class NetGateController {
    private final NetGateService gate;
    private final TestService tests;
    private final LessonService lessons;

    public NetGateController(NetGateService gate, TestService tests, LessonService lessons) {
        this.gate = gate;
        this.tests = tests;
        this.lessons = lessons;
    }

    /** 规则 + 学生名单（没有规则时 rule 为 null）。 */
    @GetMapping("/api/teacher/quiz/lessons/{lessonId}/net-gate")
    public ApiResponse<Map<String, Object>> get(@PathVariable Long lessonId) {
        Map<String, Object> out = new HashMap<>();
        out.put("rule", gate.rule(lessonId));
        out.put("students", gate.students(CurrentUser.id(), lessonId));
        return ApiResponse.ok(out);
    }

    @PutMapping("/api/teacher/quiz/lessons/{lessonId}/net-gate")
    public ApiResponse<Void> save(@PathVariable Long lessonId, @RequestBody Map<String, Object> body) {
        lessons.requireTeacher(CurrentUser.id(), lessonId);
        Body b = Body.of(body);
        Long pageId = b.reqLng("pageId", "测验页面");
        Double pass = b.dbl("passScore");
        if (pass == null) throw new IllegalArgumentException("请填写达标分数");
        Long testId = ((Number) tests.lessonPageTest(lessonId, pageId).get("id")).longValue();
        gate.save(CurrentUser.id(), lessonId, testId, pass, b.intv("maxTries", 1), b.bool("enabled", true));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/teacher/quiz/lessons/{lessonId}/net-gate")
    public ApiResponse<Void> remove(@PathVariable Long lessonId) {
        gate.remove(CurrentUser.id(), lessonId);
        return ApiResponse.ok(null);
    }

    /** 给某个学生加机会。 */
    @PostMapping("/api/teacher/quiz/lessons/{lessonId}/net-gate/students/{studentId}/tries")
    public ApiResponse<List<Map<String, Object>>> grant(@PathVariable Long lessonId, @PathVariable Long studentId,
                                                        @RequestBody(required = false) Map<String, Object> body) {
        gate.grantTry(CurrentUser.id(), lessonId, studentId, Body.of(body).intv("n", 1));
        return ApiResponse.ok(gate.students(CurrentUser.id(), lessonId));
    }

    /** 手动放行 OPEN / 手动断网 CUT / 恢复按成绩（mode 为空）。 */
    @PutMapping("/api/teacher/quiz/lessons/{lessonId}/net-gate/students/{studentId}/override")
    public ApiResponse<List<Map<String, Object>>> override(@PathVariable Long lessonId, @PathVariable Long studentId,
                                                           @RequestBody Map<String, Object> body) {
        gate.setOverride(CurrentUser.id(), lessonId, studentId, Body.of(body).str("mode"));
        return ApiResponse.ok(gate.students(CurrentUser.id(), lessonId));
    }

    /** 学生：未达标且还有机会时重做。 */
    @PostMapping("/api/student/quiz/tests/{id}/retake")
    public ApiResponse<Map<String, Object>> retake(@PathVariable Long id) {
        return ApiResponse.ok(tests.studentRetake(CurrentUser.id(), id));
    }
}
