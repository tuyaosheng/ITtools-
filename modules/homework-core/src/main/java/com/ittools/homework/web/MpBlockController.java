package com.ittools.homework.web;

import com.ittools.homework.service.MpBlockService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/** 平台图形化编程（掌控板）：学生的积木程序自动保存 / 读取，老师查看学生的积木。 */
@RestController
public class MpBlockController {
    private final MpBlockService svc;

    public MpBlockController(MpBlockService svc) {
        this.svc = svc;
    }

    private static String host(HttpServletRequest req) {
        return com.ittools.classroom.util.ServerAddress.forBoards(req.getServerName());
    }

    @GetMapping("/api/student/mpblocks/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> load(@PathVariable Long lessonId, @PathVariable Long pageId, HttpServletRequest req) {
        return ApiResponse.ok(svc.studentLoad(CurrentUser.id(), lessonId, pageId, host(req)));
    }

    @PutMapping("/api/student/mpblocks/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long lessonId, @PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        return ApiResponse.ok(svc.studentSave(CurrentUser.id(), lessonId, pageId, body.get("project"), code == null ? null : String.valueOf(code)));
    }

    /** 作品页旁边显示的任务单（老师在作品页设置里选的）。 */
    @GetMapping("/api/student/mpblocks/lessons/{lessonId}/pages/{pageId}/task")
    public ApiResponse<Map<String, Object>> task(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentTask(CurrentUser.id(), lessonId, pageId));
    }

    /** 学生随时练习（不在课里）：读取 / 自动保存自己的程序。 */
    @GetMapping("/api/student/mpblocks/free")
    public ApiResponse<Map<String, Object>> freeLoad(HttpServletRequest req) {
        return ApiResponse.ok(svc.freeLoad(CurrentUser.id(), host(req)));
    }

    @PutMapping("/api/student/mpblocks/free")
    public ApiResponse<Map<String, Object>> freeSave(@RequestBody Map<String, Object> body) {
        Object code = body.get("code");
        return ApiResponse.ok(svc.freeSave(CurrentUser.id(), body.get("project"), code == null ? null : String.valueOf(code)));
    }

    /** 学生自己以前的程序（别的课的作品页存档、随时练习、交过的 .mpcode），给“从以前的程序开始”用。 */
    @GetMapping("/api/student/mpblocks/history")
    public ApiResponse<List<Map<String, Object>>> history(@RequestParam(required = false) Long lessonId, @RequestParam(required = false) Long pageId) {
        return ApiResponse.ok(svc.history(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/student/mpblocks/history/work/{lessonId}/{pageId}")
    public ApiResponse<Map<String, Object>> historyWork(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.historyWork(CurrentUser.id(), lessonId, pageId));
    }

    /** 作品页的起始程序：老师在编辑器里搭（自动保存）或打开 .mpcode。 */
    @GetMapping("/api/teacher/mpblocks/pages/{pageId}/starter")
    public ApiResponse<Map<String, Object>> starterLoad(@PathVariable Long pageId, HttpServletRequest req) {
        return ApiResponse.ok(svc.starterLoad(CurrentUser.id(), pageId, host(req)));
    }

    @PutMapping("/api/teacher/mpblocks/pages/{pageId}/starter")
    public ApiResponse<Map<String, Object>> starterSave(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.starterSave(CurrentUser.id(), pageId, body.get("project")));
    }

    @DeleteMapping("/api/teacher/mpblocks/pages/{pageId}/starter")
    public ApiResponse<Void> starterDelete(@PathVariable Long pageId) {
        svc.starterDelete(CurrentUser.id(), pageId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/teacher/mpblocks/lessons/{lessonId}/pages/{pageId}/students/{studentId}")
    public ApiResponse<Map<String, Object>> teacherLoad(@PathVariable Long lessonId, @PathVariable Long pageId, @PathVariable Long studentId,
                                                        HttpServletRequest req) {
        return ApiResponse.ok(svc.teacherLoad(CurrentUser.id(), lessonId, pageId, studentId, host(req)));
    }
}
