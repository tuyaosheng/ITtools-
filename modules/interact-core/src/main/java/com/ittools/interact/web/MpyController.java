package com.ittools.interact.web;

import com.ittools.interact.service.MpyService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * 掌控板编程：web/public/mpython（原 mpython-lite）通过 mpl/bridge.js 访问这里。
 * 路径末段与原 server.py 一一对应：config / state / code / event / stream / starter / settings。
 * 教师的初始积木与设置在课程编辑里也能改（没有课堂），所以另有 /api/teacher/mpy/pages/{pageId}/... 一套。
 */
@RestController
public class MpyController {
    private final MpyService svc;

    public MpyController(MpyService svc) {
        this.svc = svc;
    }

    // ================= 学生 =================
    private static final String S = "/api/student/mpy/lessons/{lessonId}/pages/{pageId}";

    @GetMapping(S + "/config")
    public ApiResponse<Map<String, Object>> studentConfig(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentConfig(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping(S + "/state")
    public ApiResponse<Map<String, Object>> studentState(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentState(CurrentUser.id(), lessonId, pageId));
    }

    @PostMapping(S + "/code")
    public ApiResponse<Map<String, Object>> code(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveCode(CurrentUser.id(), lessonId, pageId, body));
    }

    /** 板子经 USB 说的话，由学生页转发（原 GET /event?group=&type=&via=usb；组号以服务器为准）。 */
    @GetMapping(S + "/event")
    public ApiResponse<Map<String, Object>> studentEvent(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestParam String type) {
        return ApiResponse.ok(svc.studentEvent(CurrentUser.id(), lessonId, pageId, type));
    }

    @GetMapping(value = S + "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter studentStream(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return svc.studentStream(CurrentUser.id(), lessonId, pageId);
    }

    @GetMapping(S + "/starter")
    public ApiResponse<Object> studentStarter(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestParam(required = false) String demo) {
        return ApiResponse.ok(svc.starterForStudent(CurrentUser.id(), lessonId, pageId, demo));
    }

    // ================= 教师：课堂中 =================
    private static final String T = "/api/teacher/mpy/lessons/{lessonId}/pages/{pageId}";

    @GetMapping(T + "/config")
    public ApiResponse<Map<String, Object>> teacherConfig(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.teacherConfig(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping(T + "/state")
    public ApiResponse<Map<String, Object>> teacherState(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.teacherState(CurrentUser.id(), lessonId, pageId));
    }

    /** 教师替某组模拟板子的话（原 GET /event?group=&type=&sim=1）。 */
    @GetMapping(T + "/event")
    public ApiResponse<Map<String, Object>> teacherEvent(@PathVariable Long lessonId, @PathVariable Long pageId,
                                                         @RequestParam String group, @RequestParam String type) {
        return ApiResponse.ok(svc.teacherEvent(CurrentUser.id(), lessonId, pageId, group, type));
    }

    @GetMapping(value = T + "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter teacherStream(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return svc.teacherStream(CurrentUser.id(), lessonId, pageId);
    }

    @GetMapping(T + "/starter")
    public ApiResponse<Object> teacherStarter(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestParam(required = false) String demo) {
        return ApiResponse.ok(svc.starterForTeacher(CurrentUser.id(), lessonId, pageId, demo));
    }

    @PostMapping(T + "/starter")
    public ApiResponse<Map<String, Object>> saveStarter(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveStarter(CurrentUser.id(), lessonId, pageId, body));
    }

    @PostMapping(T + "/settings")
    public ApiResponse<Map<String, Object>> saveSettings(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveSettings(CurrentUser.id(), lessonId, pageId, body));
    }

    // ================= 教师：课程编辑（没有课堂） =================
    private static final String P = "/api/teacher/mpy/pages/{pageId}";

    @GetMapping(P + "/config")
    public ApiResponse<Map<String, Object>> pageConfig(@PathVariable Long pageId) {
        return ApiResponse.ok(svc.teacherConfig(CurrentUser.id(), null, pageId));
    }

    @GetMapping(P + "/starter")
    public ApiResponse<Object> pageStarter(@PathVariable Long pageId, @RequestParam(required = false) String demo) {
        return ApiResponse.ok(svc.starterForTeacher(CurrentUser.id(), null, pageId, demo));
    }

    @PostMapping(P + "/starter")
    public ApiResponse<Map<String, Object>> pageSaveStarter(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveStarter(CurrentUser.id(), null, pageId, body));
    }

    @PostMapping(P + "/settings")
    public ApiResponse<Map<String, Object>> pageSaveSettings(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveSettings(CurrentUser.id(), null, pageId, body));
    }
}
