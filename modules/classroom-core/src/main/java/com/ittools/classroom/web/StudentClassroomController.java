package com.ittools.classroom.web;

import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.ClientIp;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** 学生端：当前课堂、进课签到、课堂页面、学习记录。 */
@RestController
@RequestMapping("/api/student")
public class StudentClassroomController {
    private final LessonService lessons;

    public StudentClassroomController(LessonService lessons) {
        this.lessons = lessons;
    }

    @GetMapping("/lessons/current")
    public ApiResponse<Map<String, Object>> current() {
        return ApiResponse.ok(lessons.currentLessonForStudent(CurrentUser.id()));
    }

    @PostMapping("/lessons/{id}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable Long id, HttpServletRequest req) {
        return ApiResponse.ok(lessons.join(CurrentUser.id(), id, ClientIp.of(req)));
    }

    @GetMapping("/lessons/{id}/pages")
    public ApiResponse<List<Map<String, Object>>> pages(@PathVariable Long id) {
        return ApiResponse.ok(lessons.studentPages(CurrentUser.id(), id));
    }

    @GetMapping("/lessons/{id}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> page(@PathVariable Long id, @PathVariable Long pageId) {
        return ApiResponse.ok(lessons.studentPage(CurrentUser.id(), id, pageId));
    }

    @GetMapping("/records")
    public ApiResponse<List<Map<String, Object>>> records() {
        return ApiResponse.ok(lessons.studentRecords(CurrentUser.id()));
    }
}
