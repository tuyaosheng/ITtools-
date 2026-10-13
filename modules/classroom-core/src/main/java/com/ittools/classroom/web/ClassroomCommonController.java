package com.ittools.classroom.web;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import com.ittools.classroom.service.TermService;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
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

/** 课程附件下载（师生共用，按权限校验）+ 管理员：学期维护、班级绑定教师、当前课堂一览。 */
@RestController
public class ClassroomCommonController {
    private final CourseService courses;
    private final LessonService lessons;
    private final TermService terms;
    private final ClassBindingService bindings;
    private final FileStorageService storage;
    private final com.ittools.classroom.service.EmergencyToolsService tools;

    public ClassroomCommonController(CourseService courses, LessonService lessons, TermService terms,
                                     ClassBindingService bindings, FileStorageService storage,
                                     com.ittools.classroom.service.EmergencyToolsService tools) {
        this.tools = tools;
        this.courses = courses;
        this.lessons = lessons;
        this.terms = terms;
        this.bindings = bindings;
        this.storage = storage;
    }

    @GetMapping("/api/classroom/pages/{pageId}/file")
    public ResponseEntity<Resource> pageFile(@PathVariable Long pageId, @RequestParam(defaultValue = "false") boolean inline) {
        Map<String, Object> pg = courses.page(pageId);
        AppUserDetails u = CurrentUser.get();
        if (!lessons.canReadCourse(u.userId(), u.role().name(), ((Number) pg.get("courseId")).longValue())) {
            throw new IllegalArgumentException("无权下载该文件");
        }
        if (pg.get("filePath") == null) throw new IllegalArgumentException("该页面没有附件");
        return storage.download((String) pg.get("filePath"), (String) pg.get("fileName"), inline);
    }

    // ---- 管理员 ----
    @GetMapping("/api/admin/terms")
    public ApiResponse<List<Map<String, Object>>> terms() {
        return ApiResponse.ok(terms.list());
    }

    @PostMapping("/api/admin/terms")
    public ApiResponse<Long> createTerm(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(terms.create(b.reqStr("yearName", "学年"), b.reqStr("termName", "学期"), b.intv("displayOrder", 0)));
    }

    @PutMapping("/api/admin/terms/{id}")
    public ApiResponse<Void> updateTerm(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        terms.update(id, b.reqStr("yearName", "学年"), b.reqStr("termName", "学期"), b.intv("displayOrder", 0));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/terms/{id}")
    public ApiResponse<Void> deleteTerm(@PathVariable Long id) {
        terms.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/admin/terms/{id}/current")
    public ApiResponse<Void> setCurrent(@PathVariable Long id) {
        terms.setCurrent(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/class-bindings")
    public ApiResponse<List<Map<String, Object>>> classBindings() {
        return ApiResponse.ok(bindings.list());
    }

    @PutMapping("/api/admin/class-bindings/{classId}")
    public ApiResponse<Void> bind(@PathVariable Long classId, @RequestBody Map<String, Object> body) {
        bindings.bind(classId, Body.of(body).lng("teacherId"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/lessons/active")
    public ApiResponse<List<Map<String, Object>>> activeLessons() {
        return ApiResponse.ok(lessons.activeLessons());
    }

    // ---- 管理员：应急工具 ----
    @PostMapping("/api/admin/tools/end-all")
    public ApiResponse<Integer> endAll() {
        return ApiResponse.ok(tools.endAll());
    }

    @GetMapping("/api/admin/tools/absences")
    public ApiResponse<List<Map<String, Object>>> absences(@RequestParam Long classId, @RequestParam(required = false) Long termId) {
        return ApiResponse.ok(tools.absences(classId, termId));
    }

    @PostMapping("/api/admin/tools/absences/clear")
    public ApiResponse<Integer> clearAbsences(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tools.clearAbsences(Body.of(body).maps("items")));
    }

    @PostMapping("/api/admin/tools/lessons/{id}/revoke")
    public ApiResponse<Void> revokeLesson(@PathVariable Long id) {
        tools.revokeLesson(null, id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/tools/courses")
    public ApiResponse<List<Map<String, Object>>> courses(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(tools.coursesWithUsage(keyword));
    }

    @DeleteMapping("/api/admin/tools/courses/{id}")
    public ApiResponse<Integer> forceDeleteCourse(@PathVariable Long id) {
        return ApiResponse.ok(tools.forceDeleteCourse(id));
    }

    @PostMapping("/api/admin/lessons/{id}/force-end")
    public ApiResponse<Void> forceEnd(@PathVariable Long id) {
        lessons.forceEnd(id);
        return ApiResponse.ok(null);
    }
}
