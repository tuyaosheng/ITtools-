package com.ittools.resource.web;

import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.resource.service.ReadingSiteService;
import com.ittools.resource.service.ResourceToolsService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 阅读计时、学生个人站点、指法练习、教师管理学生网盘、教案打包。 */
@RestController
public class ResourceExtrasController {
    /** 学生站点一律作为沙箱文档运行，不能读取平台会话。 */
    static final String SANDBOX = "sandbox allow-scripts allow-forms allow-popups allow-modals";

    private final ReadingSiteService reading;
    private final ResourceToolsService tools;
    private final FileStorageService storage;

    public ResourceExtrasController(ReadingSiteService reading, ResourceToolsService tools, FileStorageService storage) {
        this.reading = reading;
        this.tools = tools;
        this.storage = storage;
    }

    // ---- 阅读计时 ----
    @PostMapping("/api/student/resources/{id}/open")
    public ApiResponse<Map<String, Object>> open(@PathVariable Long id) {
        return ApiResponse.ok(reading.open(CurrentUser.id(), id));
    }

    @PostMapping("/api/student/resources/{id}/read")
    public ApiResponse<Map<String, Object>> read(@PathVariable Long id) {
        return ApiResponse.ok(reading.beat(CurrentUser.id(), id));
    }

    @GetMapping("/api/student/resources/reading")
    public ApiResponse<Map<Long, Map<String, Object>>> myReading() {
        return ApiResponse.ok(reading.myReading(CurrentUser.id()));
    }

    @GetMapping("/api/teacher/resources/{id}/reading")
    public ApiResponse<List<Map<String, Object>>> readingReport(@PathVariable Long id, @RequestParam Long classId) {
        return ApiResponse.ok(reading.readingReport(CurrentUser.id(), id, classId));
    }

    // ---- 学生个人站点 ----
    @GetMapping("/api/student/site")
    public ApiResponse<Map<String, Object>> mySite() {
        return ApiResponse.ok(reading.mySite(CurrentUser.id()));
    }

    @PostMapping("/api/student/site")
    public ApiResponse<Map<String, Object>> uploadSite(@RequestParam("file") MultipartFile file, @RequestParam(required = false) String title) throws IOException {
        return ApiResponse.ok(reading.uploadSite(CurrentUser.id(), file, title));
    }

    @PutMapping("/api/student/site")
    public ApiResponse<Map<String, Object>> renameSite(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(reading.renameSite(CurrentUser.id(), Body.of(body).str("title")));
    }

    @DeleteMapping("/api/student/site")
    public ApiResponse<Void> deleteSite() {
        reading.deleteSite(CurrentUser.id());
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/sites/class")
    public ApiResponse<List<Map<String, Object>>> classSites(@RequestParam(required = false) Long classId) {
        AppUserDetails u = CurrentUser.get();
        return ApiResponse.ok(reading.classSites(u.userId(), u.role().name(), classId));
    }

    @DeleteMapping("/api/teacher/sites/{studentId}")
    public ApiResponse<Void> teacherDeleteSite(@PathVariable Long studentId) {
        reading.teacherDeleteSite(CurrentUser.id(), studentId);
        return ApiResponse.ok(null);
    }

    @GetMapping("/site/{siteId}/**")
    public ResponseEntity<Resource> siteFile(@PathVariable String siteId, HttpServletRequest req) throws IOException {
        String prefix = "/site/" + siteId + "/";
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        if (!uri.startsWith(prefix)) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        Path f = reading.resolveSite(siteId, URLDecoder.decode(uri.substring(prefix.length()).replace("+", "%2B"), "UTF-8"));
        if (f == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(f.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES))
                .header("Content-Security-Policy", SANDBOX)
                .header("Access-Control-Allow-Origin", "*")
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(f));
    }

    // ---- 指法练习 ----
    @PostMapping("/api/student/typing")
    public ApiResponse<Map<String, Object>> saveTyping(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tools.saveTyping(CurrentUser.id(), body));
    }

    @GetMapping("/api/student/typing")
    public ApiResponse<List<Map<String, Object>>> myTyping() {
        return ApiResponse.ok(tools.myTyping(CurrentUser.id()));
    }

    @GetMapping("/api/typing/leaderboard")
    public ApiResponse<List<Map<String, Object>>> leaderboard(@RequestParam String level, @RequestParam(required = false) Long yearId,
                                                             @RequestParam(required = false) Long classId) {
        return ApiResponse.ok(tools.leaderboard(level, yearId, classId));
    }

    // ---- 教师管理学生网盘 ----
    @GetMapping("/api/teacher/disks/classes/{classId}")
    public ApiResponse<List<Map<String, Object>>> classDisks(@PathVariable Long classId) {
        return ApiResponse.ok(tools.classDisks(CurrentUser.id(), classId));
    }

    @GetMapping("/api/teacher/disks/students/{studentId}")
    public ApiResponse<List<Map<String, Object>>> studentFiles(@PathVariable Long studentId) {
        return ApiResponse.ok(tools.studentFiles(CurrentUser.id(), studentId));
    }

    @GetMapping("/api/teacher/disks/students/{studentId}/files/{fileId}")
    public ResponseEntity<Resource> studentFile(@PathVariable Long studentId, @PathVariable Long fileId) {
        Map<String, Object> f = tools.studentFile(CurrentUser.id(), studentId, fileId);
        return storage.download((String) f.get("file_path"), (String) f.get("file_name"), false);
    }

    @DeleteMapping("/api/teacher/disks/students/{studentId}/files/{fileId}")
    public ApiResponse<Void> deleteStudentFile(@PathVariable Long studentId, @PathVariable Long fileId, @RequestParam(defaultValue = "false") boolean penalty) {
        tools.deleteStudentFile(CurrentUser.id(), studentId, fileId, penalty);
        return ApiResponse.ok(null);
    }

    @PutMapping("/api/teacher/disks/students/{studentId}/policy")
    public ApiResponse<Void> policy(@PathVariable Long studentId, @RequestBody Map<String, Object> body) {
        Object d = body.get("disabled");
        tools.setPolicy(CurrentUser.id(), studentId, d instanceof Boolean ? (Boolean) d : null, Boolean.TRUE.equals(body.get("resetPenalty")));
        return ApiResponse.ok(null);
    }

    // ---- 教案打包 / 导入 ----
    @GetMapping("/api/teacher/lesson-plans/{id}/pack")
    public ResponseEntity<StreamingResponseBody> planPack(@PathVariable Long id) {
        Map<String, Object> r = tools.planPackInfo(CurrentUser.id(), id);
        StreamingResponseBody body = out -> tools.writePlanPack(r, out);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(r.get("title") + ".教案.zip", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping("/api/teacher/lesson-plans/import")
    public ApiResponse<Long> importPlan(@RequestParam("file") MultipartFile file, @RequestParam(required = false) Long termId) throws IOException {
        return ApiResponse.ok(tools.importPlanPack(CurrentUser.id(), file, termId));
    }
}
