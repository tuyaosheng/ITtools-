package com.ittools.homework.web;

import com.ittools.homework.service.HomeworkService;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.platform.web.ClientIp;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
public class HomeworkController {
    private final HomeworkService svc;
    private final FileStorageService storage;

    public HomeworkController(HomeworkService svc, FileStorageService storage) {
        this.svc = svc;
        this.storage = storage;
    }

    // ---- 教师 ----
    @GetMapping("/api/teacher/homework/pages/{pageId}/config")
    public ApiResponse<Map<String, Object>> config(@PathVariable Long pageId) {
        return ApiResponse.ok(svc.config(pageId));
    }

    @PutMapping("/api/teacher/homework/pages/{pageId}/config")
    public ApiResponse<Map<String, Object>> saveConfig(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveConfig(CurrentUser.id(), pageId, body));
    }

    @GetMapping("/api/teacher/homework/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> pageSubmissions(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.pageSubmissions(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/teacher/homework/lessons/{lessonId}/summary")
    public ApiResponse<List<Map<String, Object>>> summary(@PathVariable Long lessonId) {
        return ApiResponse.ok(svc.lessonSummary(CurrentUser.id(), lessonId));
    }

    @GetMapping("/api/teacher/homework/pending")
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(svc.pendingForTeacher(CurrentUser.id()));
    }

    @PutMapping("/api/teacher/homework/submissions/{id}/grade")
    @SuppressWarnings("unchecked")
    public ApiResponse<Void> grade(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        Object rs = body.get("rubricScores");
        svc.grade(CurrentUser.id(), id, b.dbl("score"), rs instanceof Map ? (Map<String, Object>) rs : null, b.str("comment"), b.str("rating"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/homework/submissions/{id}/return")
    public ApiResponse<Void> returnForRedo(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        svc.returnForRedo(CurrentUser.id(), id, Body.of(body).str("note"));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/homework/submissions/batch-grade")
    public ApiResponse<Integer> batchGrade(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(svc.batchGrade(CurrentUser.id(), b.longs("ids"), b.dbl("score"), b.str("comment"), b.str("rating"), b.bool("skipGraded", false)));
    }

    @PutMapping("/api/teacher/homework/submissions/{id}/essence")
    public ApiResponse<Void> essence(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        svc.setEssence(CurrentUser.id(), id, Body.of(body).bool("essence", true));
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/homework/lessons/{lessonId}/pages/{pageId}/regrade")
    public ApiResponse<Integer> regrade(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.regrade(CurrentUser.id(), lessonId, pageId));
    }

    @GetMapping("/api/teacher/homework/lessons/{lessonId}/pages/{pageId}/zip")
    public ResponseEntity<byte[]> zip(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("作品打包_" + lessonId + "_" + pageId + ".zip", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(svc.zip(CurrentUser.id(), lessonId, pageId));
    }

    // ---- 学生 ----
    @GetMapping("/api/student/homework/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> studentView(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.studentView(CurrentUser.id(), lessonId, pageId));
    }

    @PostMapping("/api/student/homework/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> submit(@PathVariable Long lessonId, @PathVariable Long pageId,
                                                   @RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "description", required = false) String description,
                                                   @RequestParam(value = "title", required = false) String title,
                                                   @RequestParam(value = "selfScore", required = false) Integer selfScore,
                                                   HttpServletRequest req) {
        return ApiResponse.ok(svc.submit(CurrentUser.id(), lessonId, pageId, file, description, ClientIp.of(req), title, selfScore));
    }

    @GetMapping("/api/student/homework/lessons/{lessonId}/pages/{pageId}/classmates")
    public ApiResponse<List<Map<String, Object>>> classmates(@PathVariable Long lessonId, @PathVariable Long pageId) {
        return ApiResponse.ok(svc.classmates(CurrentUser.id(), lessonId, pageId));
    }

    @PostMapping("/api/student/homework/submissions/{id}/review")
    public ApiResponse<Void> review(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        svc.peerReview(CurrentUser.id(), id, b.intv("score", -1), b.str("comment"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/student/homework/late")
    public ApiResponse<List<Map<String, Object>>> late() {
        return ApiResponse.ok(svc.lateList(CurrentUser.id()));
    }

    @GetMapping("/api/student/homework/mine")
    public ApiResponse<List<Map<String, Object>>> mine() {
        return ApiResponse.ok(svc.myWorks(CurrentUser.id()));
    }

    // ---- 公共 ----
    @GetMapping("/api/teacher/homework/lessons/{lessonId}/group-stats")
    public ApiResponse<List<Map<String, Object>>> groupStats(@PathVariable Long lessonId) {
        return ApiResponse.ok(svc.groupStats(CurrentUser.id(), lessonId));
    }

    @GetMapping("/api/homework/essence-ranking")
    public ApiResponse<List<Map<String, Object>>> ranking(@RequestParam(required = false) Long yearId, @RequestParam(required = false) Integer days) {
        return ApiResponse.ok(svc.essenceRanking(yearId, days));
    }

    @GetMapping("/api/homework/showcase")
    public ApiResponse<List<Map<String, Object>>> showcase(@RequestParam(required = false) Long yearId) {
        return ApiResponse.ok(svc.showcase(yearId));
    }

    @GetMapping("/api/homework/submissions/{id}/file")
    public ResponseEntity<Resource> file(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean inline) {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> s = svc.downloadable(u.userId(), u.role().name(), id);
        return storage.download((String) s.get("filePath"), (String) s.get("fileName"), inline);
    }
}
