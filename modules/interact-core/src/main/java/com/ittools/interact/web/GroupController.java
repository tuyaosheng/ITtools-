package com.ittools.interact.web;

import com.ittools.interact.service.GroupService;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
public class GroupController {
    private final GroupService svc;
    private final FileStorageService storage;

    public GroupController(GroupService svc, FileStorageService storage) {
        this.svc = svc;
        this.storage = storage;
    }

    // ================= 教师：分组管理 =================
    @GetMapping("/api/teacher/groups/lessons/{id}")
    public ApiResponse<Map<String, Object>> overview(@PathVariable Long id) {
        return ApiResponse.ok(svc.teacherOverview(CurrentUser.id(), id));
    }

    @PutMapping("/api/teacher/groups/lessons/{id}/settings")
    public ApiResponse<Map<String, Object>> settings(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.updateSettings(CurrentUser.id(), id, b.intv("groupCount"),
                b.has("joinOpen") ? b.bool("joinOpen", false) : null, b.has("voteOpen") ? b.bool("voteOpen", false) : null));
    }

    @PutMapping("/api/teacher/groups/lessons/{id}/members/{studentId}")
    public ApiResponse<Map<String, Object>> assign(@PathVariable Long id, @PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.assign(CurrentUser.id(), id, studentId, Body.of(body).intv("groupNo", 0)));
    }

    @PutMapping("/api/teacher/groups/lessons/{id}/groups/{groupNo}/leader")
    public ApiResponse<Map<String, Object>> leader(@PathVariable Long id, @PathVariable int groupNo, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.setLeader(CurrentUser.id(), id, groupNo, Body.of(body).lng("studentId")));
    }

    @PostMapping("/api/teacher/groups/lessons/{id}/auto-leaders")
    public ApiResponse<Map<String, Object>> autoLeaders(@PathVariable Long id) {
        return ApiResponse.ok(svc.autoLeaders(CurrentUser.id(), id));
    }

    @PostMapping("/api/teacher/groups/lessons/{id}/random")
    public ApiResponse<Map<String, Object>> random(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.randomize(CurrentUser.id(), id, Body.of(body).intv("groupCount", 0)));
    }

    @PostMapping("/api/teacher/groups/lessons/{id}/clear")
    public ApiResponse<Map<String, Object>> clear(@PathVariable Long id) {
        return ApiResponse.ok(svc.clear(CurrentUser.id(), id));
    }

    @GetMapping("/api/teacher/groups/lessons/{id}/history")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable Long id) {
        return ApiResponse.ok(svc.history(CurrentUser.id(), id));
    }

    @PostMapping("/api/teacher/groups/lessons/{id}/load")
    public ApiResponse<Map<String, Object>> load(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.loadHistory(CurrentUser.id(), id, Body.of(body).reqLng("fromLessonId", "历史课堂")));
    }

    @GetMapping("/api/teacher/groups/lessons/{id}/work")
    public ApiResponse<Map<String, Object>> teacherWork(@PathVariable Long id) {
        return ApiResponse.ok(svc.teacherWork(CurrentUser.id(), id));
    }

    // ================= 学生：分组 / 推选 =================
    @GetMapping("/api/student/groups/lessons/{id}")
    public ApiResponse<Map<String, Object>> studentOverview(@PathVariable Long id) {
        return ApiResponse.ok(svc.studentOverview(CurrentUser.id(), id));
    }

    @PostMapping("/api/student/groups/lessons/{id}/join")
    public ApiResponse<Map<String, Object>> join(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.studentJoin(CurrentUser.id(), id, Body.of(body).intv("groupNo", 0)));
    }

    @PostMapping("/api/student/groups/lessons/{id}/vote")
    public ApiResponse<Map<String, Object>> vote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.studentVote(CurrentUser.id(), id, Body.of(body).lng("candidateId")));
    }

    // ================= 学生：小组合作 =================
    @GetMapping("/api/student/groups/lessons/{id}/work")
    public ApiResponse<Map<String, Object>> work(@PathVariable Long id) {
        return ApiResponse.ok(svc.myWork(CurrentUser.id(), id));
    }

    @PutMapping("/api/student/groups/lessons/{id}/work/topic")
    public ApiResponse<Map<String, Object>> topic(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.setTopic(CurrentUser.id(), id, Body.of(body).str("topic")));
    }

    @PutMapping("/api/student/groups/lessons/{id}/work/tasks")
    public ApiResponse<Map<String, Object>> tasks(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.setTasks(CurrentUser.id(), id, Body.of(body).maps("tasks")));
    }

    @PostMapping("/api/student/groups/lessons/{id}/work/files")
    public ApiResponse<Map<String, Object>> upload(@PathVariable Long id, @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "title", required = false) String title,
                                                   @RequestParam(value = "intro", required = false) String intro) {
        return ApiResponse.ok(svc.uploadFile(CurrentUser.id(), id, file, title, intro));
    }

    @DeleteMapping("/api/student/groups/lessons/{id}/work/files/{fileId}")
    public ApiResponse<Map<String, Object>> deleteFile(@PathVariable Long id, @PathVariable Long fileId) {
        return ApiResponse.ok(svc.deleteFile(CurrentUser.id(), id, fileId));
    }

    @PutMapping("/api/student/groups/lessons/{id}/work/result")
    public ApiResponse<Map<String, Object>> result(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.setResult(CurrentUser.id(), id, b.str("resultText"), b.lng("resultFileId")));
    }

    @PutMapping("/api/student/groups/lessons/{id}/work/peers/{targetId}")
    public ApiResponse<Map<String, Object>> peer(@PathVariable Long id, @PathVariable Long targetId, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.evaluatePeer(CurrentUser.id(), id, targetId, b.intv("score", 0), b.str("comment")));
    }

    // ================= 学生：小组成果展示 =================
    @GetMapping("/api/student/groups/lessons/{id}/show")
    public ApiResponse<Map<String, Object>> show(@PathVariable Long id) {
        return ApiResponse.ok(svc.showcase(CurrentUser.id(), id));
    }

    @PutMapping("/api/student/groups/lessons/{id}/show/{groupNo}")
    public ApiResponse<Map<String, Object>> review(@PathVariable Long id, @PathVariable int groupNo, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.review(CurrentUser.id(), id, groupNo, b.intv("score", -1), b.str("comment")));
    }

    // ================= 文件下载 =================
    @GetMapping("/api/groups/files/{fileId}")
    public ResponseEntity<Resource> file(@PathVariable Long fileId, @RequestParam(defaultValue = "false") boolean inline) {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> f = svc.fileForDownload(u.userId(), u.role().name(), fileId);
        return storage.download((String) f.get("file_path"), (String) f.get("file_name"), inline);
    }
}
