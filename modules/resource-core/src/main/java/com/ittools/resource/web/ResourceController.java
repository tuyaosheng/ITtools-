package com.ittools.resource.web;

import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.resource.service.ElectiveService;
import com.ittools.resource.service.ResourceService;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class ResourceController {
    private final ResourceService svc;
    private final ElectiveService electives;
    private final FileStorageService storage;

    public ResourceController(ResourceService svc, ElectiveService electives, FileStorageService storage) {
        this.svc = svc;
        this.electives = electives;
        this.storage = storage;
    }

    // ================= 教学资源 =================
    @GetMapping("/api/teacher/resources")
    public ApiResponse<List<Map<String, Object>>> resources(@RequestParam(defaultValue = "false") boolean shared,
                                                            @RequestParam(required = false) String category,
                                                            @RequestParam(required = false) Long termId) {
        return ApiResponse.ok(svc.teacherResources(CurrentUser.id(), shared, category, termId));
    }

    @GetMapping("/api/teacher/resources/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(svc.categories());
    }

    @PostMapping("/api/teacher/resources")
    public ApiResponse<Map<String, Object>> createResource(@RequestParam Map<String, Object> form,
                                                           @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(svc.saveResource(CurrentUser.id(), null, form, file));
    }

    @PostMapping("/api/teacher/resources/{id}")
    public ApiResponse<Map<String, Object>> updateResource(@PathVariable Long id, @RequestParam Map<String, Object> form,
                                                           @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.ok(svc.saveResource(CurrentUser.id(), id, form, file));
    }

    @PutMapping("/api/teacher/resources/{id}/classes")
    public ApiResponse<Void> distribute(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        svc.distribute(CurrentUser.id(), id, Body.of(body).longs("classIds"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/teacher/resources/{id}")
    public ApiResponse<Void> deleteResource(@PathVariable Long id) {
        svc.deleteResource(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/student/resources")
    public ApiResponse<List<Map<String, Object>>> studentResources() {
        return ApiResponse.ok(svc.studentResources(CurrentUser.id()));
    }

    @GetMapping("/api/resources/{id}/file")
    public ResponseEntity<Resource> resourceFile(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean inline) {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> r = svc.resourceFile(u.userId(), u.role().name(), id);
        return storage.download((String) r.get("file_path"), (String) r.get("file_name"), inline);
    }

    // ================= 教案 =================
    @GetMapping("/api/teacher/lesson-plans")
    public ApiResponse<List<Map<String, Object>>> plans(@RequestParam(defaultValue = "false") boolean shared,
                                                        @RequestParam(required = false) Long termId) {
        return ApiResponse.ok(svc.plans(CurrentUser.id(), shared, termId));
    }

    @PostMapping("/api/teacher/lesson-plans")
    public ApiResponse<Map<String, Object>> createPlan(@RequestParam Map<String, Object> form,
                                                       @RequestParam(value = "doc", required = false) MultipartFile doc,
                                                       @RequestParam(value = "ppt", required = false) MultipartFile ppt) {
        return ApiResponse.ok(svc.savePlan(CurrentUser.id(), null, form, doc, ppt));
    }

    @PostMapping("/api/teacher/lesson-plans/{id}")
    public ApiResponse<Map<String, Object>> updatePlan(@PathVariable Long id, @RequestParam Map<String, Object> form,
                                                       @RequestParam(value = "doc", required = false) MultipartFile doc,
                                                       @RequestParam(value = "ppt", required = false) MultipartFile ppt) {
        return ApiResponse.ok(svc.savePlan(CurrentUser.id(), id, form, doc, ppt));
    }

    @PostMapping("/api/teacher/lesson-plans/{id}/copy")
    public ApiResponse<Long> copyPlan(@PathVariable Long id) {
        return ApiResponse.ok(svc.copyPlan(CurrentUser.id(), id));
    }

    @DeleteMapping("/api/teacher/lesson-plans/{id}")
    public ApiResponse<Void> deletePlan(@PathVariable Long id) {
        svc.deletePlan(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/teacher/lesson-plans/{id}/{kind}")
    public ResponseEntity<Resource> planFile(@PathVariable Long id, @PathVariable String kind,
                                             @RequestParam(defaultValue = "false") boolean inline) {
        Map<String, Object> r = svc.planFile(CurrentUser.id(), id, kind);
        return storage.download((String) r.get("path"), (String) r.get("name"), inline);
    }

    // ================= 网盘（师生） =================
    @GetMapping("/api/disk")
    public ApiResponse<Map<String, Object>> disk() {
        return ApiResponse.ok(svc.disk(CurrentUser.id()));
    }

    @PostMapping("/api/disk")
    public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
                                                   @RequestParam(value = "title", required = false) String title,
                                                   @RequestParam(value = "note", required = false) String note) {
        AppUserDetails u = CurrentUser.get();
        return ApiResponse.ok(svc.diskUpload(u.userId(), u.role().name(), file, title, note));
    }

    @DeleteMapping("/api/disk/{id}")
    public ApiResponse<Void> deleteDisk(@PathVariable Long id) {
        svc.diskDelete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/disk/{id}/file")
    public ResponseEntity<Resource> diskFile(@PathVariable Long id) {
        Map<String, Object> r = svc.diskFile(CurrentUser.id(), id);
        return storage.download((String) r.get("file_path"), (String) r.get("file_name"), false);
    }

    // ================= 笔记 / 反思 =================
    @GetMapping("/api/notes")
    public ApiResponse<Map<String, Object>> notes() {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> m = new HashMap<>();
        m.put("notes", svc.myNotes(u.userId()));
        m.put("courses", "STUDENT".equals(u.role().name()) ? svc.studentCourses(u.userId()) : null);
        return ApiResponse.ok(m);
    }

    @PostMapping("/api/notes")
    public ApiResponse<Map<String, Object>> createNote(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveNote(CurrentUser.id(), null, body));
    }

    @PutMapping("/api/notes/{id}")
    public ApiResponse<Map<String, Object>> updateNote(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(svc.saveNote(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/api/notes/{id}")
    public ApiResponse<Void> deleteNote(@PathVariable Long id) {
        svc.deleteNote(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/teacher/reflections")
    public ApiResponse<List<Map<String, Object>>> reflections() {
        return ApiResponse.ok(svc.reflectionsForTeacher(CurrentUser.id()));
    }

    @PutMapping("/api/teacher/reflections/{id}")
    public ApiResponse<Void> comment(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        svc.commentReflection(CurrentUser.id(), id, Body.of(body).str("comment"));
        return ApiResponse.ok(null);
    }

    // ================= 走班选课 =================
    @GetMapping("/api/admin/electives")
    public ApiResponse<List<Map<String, Object>>> activities() {
        return ApiResponse.ok(electives.activities());
    }

    @GetMapping("/api/admin/electives/{id}")
    public ApiResponse<Map<String, Object>> activity(@PathVariable Long id) {
        Map<String, Object> a = electives.activity(id);
        a.put("roster", electives.roster(id));
        return ApiResponse.ok(a);
    }

    @PostMapping("/api/admin/electives")
    public ApiResponse<Map<String, Object>> createActivity(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(electives.save(CurrentUser.id(), null, b.str("name"), b.str("description"), b.lng("yearId"), b.maps("subjects")));
    }

    @PutMapping("/api/admin/electives/{id}")
    public ApiResponse<Map<String, Object>> updateActivity(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(electives.save(CurrentUser.id(), id, b.str("name"), b.str("description"), b.lng("yearId"), b.maps("subjects")));
    }

    @PutMapping("/api/admin/electives/{id}/status")
    public ApiResponse<Void> activityStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        electives.setStatus(id, Body.of(body).str("status"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/admin/electives/{id}")
    public ApiResponse<Void> deleteActivity(@PathVariable Long id) {
        electives.delete(id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/admin/electives/{id}/export")
    public ResponseEntity<byte[]> exportRoster(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("选课名单_" + id + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(electives.exportRoster(id));
    }

    @GetMapping("/api/student/electives")
    public ApiResponse<List<Map<String, Object>>> studentElectives() {
        return ApiResponse.ok(electives.studentActivities(CurrentUser.id()));
    }

    @PutMapping("/api/student/electives/{id}")
    public ApiResponse<Void> choose(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        electives.choose(CurrentUser.id(), id, Body.of(body).lng("subjectId"));
        return ApiResponse.ok(null);
    }
}
