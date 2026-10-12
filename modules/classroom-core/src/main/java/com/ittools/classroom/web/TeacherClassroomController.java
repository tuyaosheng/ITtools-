package com.ittools.classroom.web;

import com.ittools.classroom.service.ClassBindingService;
import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import com.ittools.classroom.service.TeacherToolsService;
import com.ittools.classroom.service.TermService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 教师端：课程、页面、开课、课堂控制台、我的班级、课表。 */
@RestController
@RequestMapping("/api/teacher")
public class TeacherClassroomController {
    private final CourseService courses;
    private final LessonService lessons;
    private final TermService terms;
    private final ClassBindingService bindings;
    private final TeacherToolsService tools;
    private final com.ittools.platform.service.LoginLogService loginLogService;

    public TeacherClassroomController(CourseService courses, LessonService lessons, TermService terms,
                                      ClassBindingService bindings, TeacherToolsService tools,
                                      com.ittools.platform.service.LoginLogService loginLogService) {
        this.loginLogService = loginLogService;
        this.courses = courses;
        this.lessons = lessons;
        this.terms = terms;
        this.bindings = bindings;
        this.tools = tools;
    }

    @GetMapping("/terms")
    public ApiResponse<List<Map<String, Object>>> terms() {
        return ApiResponse.ok(terms.list());
    }

    /** 学生登入记录（原 stulog）：from/to 为 yyyy-MM-dd，含首尾两天。 */
    @GetMapping("/login-logs")
    public ApiResponse<java.util.List<Map<String, Object>>> loginLogs(@RequestParam Long classId, @RequestParam String from, @RequestParam String to) {
        if (!bindings.teacherCanUseClass(CurrentUser.id(), classId)) throw new IllegalArgumentException("无权查看该班级");
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        java.time.OffsetDateTime f = java.time.LocalDate.parse(from).atStartOfDay(zone).toOffsetDateTime();
        java.time.OffsetDateTime t = java.time.LocalDate.parse(to).plusDays(1).atStartOfDay(zone).toOffsetDateTime();
        if (t.isBefore(f)) throw new IllegalArgumentException("结束日期不能早于开始日期");
        return ApiResponse.ok(loginLogService.classLogs(classId, f, t));
    }

    @GetMapping("/classes")
    public ApiResponse<List<Map<String, Object>>> classes() {
        return ApiResponse.ok(bindings.classesForTeacher(CurrentUser.id()));
    }

    // ---- 课程 ----
    @GetMapping("/courses")
    public ApiResponse<List<Map<String, Object>>> courses(@RequestParam(required = false) Long termId) {
        return ApiResponse.ok(courses.myCourses(CurrentUser.id(), termId));
    }

    @GetMapping("/courses/shared")
    public ApiResponse<List<Map<String, Object>>> shared() {
        return ApiResponse.ok(courses.sharedCourses(CurrentUser.id()));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<Map<String, Object>> course(@PathVariable Long id) {
        Map<String, Object> c = new HashMap<>(courses.getReadable(CurrentUser.id(), id));
        c.put("pages", courses.pages(id));
        return ApiResponse.ok(c);
    }

    @PostMapping("/courses")
    public ApiResponse<Map<String, Object>> createCourse(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(courses.create(CurrentUser.id(), b.reqStr("name", "课程名称"), b.str("category"),
                b.str("description"), b.lng("termId"), b.bool("shared", false)));
    }

    @PutMapping("/courses/{id}")
    public ApiResponse<Map<String, Object>> updateCourse(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(courses.update(CurrentUser.id(), id, b.reqStr("name", "课程名称"), b.str("category"),
                b.str("description"), b.lng("termId"), b.bool("shared", false)));
    }

    @DeleteMapping("/courses/{id}")
    public ApiResponse<Void> deleteCourse(@PathVariable Long id) {
        courses.delete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/courses/{id}/copy")
    public ApiResponse<Map<String, Object>> copyCourse(@PathVariable Long id, @RequestParam(required = false) Long termId) {
        return ApiResponse.ok(courses.copy(CurrentUser.id(), id, termId));
    }

    @PostMapping("/courses/{id}/pages")
    public ApiResponse<Map<String, Object>> createPage(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(courses.createPage(CurrentUser.id(), id, body));
    }

    @PutMapping("/courses/{id}/pages/order")
    public ApiResponse<List<Map<String, Object>>> reorder(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(courses.reorder(CurrentUser.id(), id, Body.of(body).longs("ids")));
    }

    @PutMapping("/pages/{pageId}")
    public ApiResponse<Map<String, Object>> updatePage(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(courses.updatePage(CurrentUser.id(), pageId, body));
    }

    @DeleteMapping("/pages/{pageId}")
    public ApiResponse<Void> deletePage(@PathVariable Long pageId) {
        courses.deletePage(CurrentUser.id(), pageId);
        return ApiResponse.ok(null);
    }

    @PostMapping("/pages/{pageId}/file")
    public ApiResponse<Map<String, Object>> attach(@PathVariable Long pageId, @RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(courses.attachFile(CurrentUser.id(), pageId, file));
    }

    // ---- 开课与课堂控制台 ----
    @PostMapping("/lessons")
    public ApiResponse<Map<String, Object>> start(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(lessons.start(CurrentUser.id(), b.reqLng("courseId", "课程"), b.reqLng("classId", "班级"),
                b.lng("groupModelId")));
    }

    @GetMapping("/lessons")
    public ApiResponse<List<Map<String, Object>>> lessons(@RequestParam(required = false) String status,
                                                          @RequestParam(required = false) Long courseId) {
        return ApiResponse.ok(lessons.teacherLessons(CurrentUser.id(), status, courseId));
    }

    @GetMapping("/lessons/{id}")
    public ApiResponse<Map<String, Object>> lesson(@PathVariable Long id) {
        Map<String, Object> l = new HashMap<>(lessons.requireTeacher(CurrentUser.id(), id));
        l.put("pages", lessons.lessonPages(id));
        return ApiResponse.ok(l);
    }

    @PostMapping("/lessons/{id}/end")
    public ApiResponse<Map<String, Object>> end(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(lessons.end(CurrentUser.id(), id, Body.of(body).str("summary")));
    }

    @PutMapping("/lessons/{id}/pages/{pageId}")
    public ApiResponse<Void> setPageOpen(@PathVariable Long id, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        lessons.setPageOpen(CurrentUser.id(), id, pageId, Body.of(body).bool("open", true));
        return ApiResponse.ok(null);
    }

    @GetMapping("/lessons/{id}/attendance")
    public ApiResponse<List<Map<String, Object>>> attendance(@PathVariable Long id) {
        lessons.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(lessons.attendance(id));
    }

    @PutMapping("/lessons/{id}/attendance/{studentId}")
    public ApiResponse<Void> updateAttendance(@PathVariable Long id, @PathVariable Long studentId,
                                              @RequestBody Map<String, Object> body) {
        lessons.updateAttendance(CurrentUser.id(), id, studentId, body);
        return ApiResponse.ok(null);
    }

    @PostMapping("/lessons/{id}/load-groups")
    public ApiResponse<List<Map<String, Object>>> loadGroups(@PathVariable Long id, @RequestParam(required = false) Long groupModelId) {
        return ApiResponse.ok(lessons.loadGroups(CurrentUser.id(), id, groupModelId));
    }

    @GetMapping("/lessons/{id}/seat-map")
    public ApiResponse<Map<String, Object>> seatMap(@PathVariable Long id) {
        lessons.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(lessons.seatMap(id));
    }

    // ---- 我的班级 ----
    @GetMapping("/classes/{classId}/students")
    public ApiResponse<List<Map<String, Object>>> students(@PathVariable Long classId) {
        return ApiResponse.ok(tools.students(CurrentUser.id(), classId));
    }

    @PostMapping("/classes/{classId}/students")
    public ApiResponse<Long> addStudent(@PathVariable Long classId, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(tools.addStudent(CurrentUser.id(), classId, b.reqStr("name", "姓名"), b.str("studentNo"), b.str("password")));
    }

    @PostMapping("/students/{studentId}/reset-password")
    public ApiResponse<Void> resetPassword(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        tools.resetStudentPassword(CurrentUser.id(), studentId, Body.of(body).str("password"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/students/{studentId}/records")
    public ApiResponse<List<Map<String, Object>>> studentRecords(@PathVariable Long studentId) {
        return ApiResponse.ok(tools.studentRecords(CurrentUser.id(), studentId));
    }

    // ---- 课表 ----
    @GetMapping("/timetable")
    public ApiResponse<List<Map<String, Object>>> timetable() {
        return ApiResponse.ok(tools.timetable(CurrentUser.id()));
    }

    @PutMapping("/timetable")
    public ApiResponse<List<Map<String, Object>>> saveTimetable(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tools.saveTimetable(CurrentUser.id(), Body.of(body).maps("cells")));
    }
}
