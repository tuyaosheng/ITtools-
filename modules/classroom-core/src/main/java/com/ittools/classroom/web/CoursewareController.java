package com.ittools.classroom.web;

import com.ittools.classroom.service.CoursewareService;
import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
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
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 课件包页面（Storyline 等）：上传解压、设置入口页、课堂中以 iframe 播放。 */
@RestController
public class CoursewareController {
    /** 课件包与平台会话隔离：作为沙箱文档运行（不同源），可运行脚本、表单、弹窗。 */
    static final String SANDBOX = "sandbox allow-scripts allow-forms allow-popups allow-modals allow-downloads allow-pointer-lock";

    private final CoursewareService courseware;
    private final LessonService lessons;

    public CoursewareController(CoursewareService courseware, LessonService lessons) {
        this.courseware = courseware;
        this.lessons = lessons;
    }

    @GetMapping("/api/teacher/courseware/pages/{pageId}")
    public ApiResponse<Map<String, Object>> teacherView(@PathVariable Long pageId) {
        Map<String, Object> m = new LinkedHashMap<>(courseware.publicConfig(pageId));
        m.put("htmlFiles", courseware.htmlFiles(CurrentUser.id(), pageId));
        return ApiResponse.ok(m);
    }

    @PostMapping("/api/teacher/courseware/pages/{pageId}")
    public ApiResponse<Map<String, Object>> upload(@PathVariable Long pageId, @RequestParam("file") MultipartFile file,
                                                   @RequestParam(required = false) String entry) {
        courseware.importPackage(CurrentUser.id(), pageId, file, entry);
        return teacherView(pageId);
    }

    @PutMapping("/api/teacher/courseware/pages/{pageId}/entry")
    public ApiResponse<Map<String, Object>> entry(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        courseware.setEntry(CurrentUser.id(), pageId, body.get("entry") == null ? null : String.valueOf(body.get("entry")));
        return teacherView(pageId);
    }

    @PutMapping("/api/teacher/courseware/pages/{pageId}/compat")
    public ApiResponse<Map<String, Object>> compat(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        courseware.setCompat(CurrentUser.id(), pageId, Boolean.TRUE.equals(body.get("compat")));
        return teacherView(pageId);
    }

    @GetMapping("/api/student/courseware/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> studentView(@PathVariable Long lessonId, @PathVariable Long pageId) {
        lessons.studentPage(CurrentUser.id(), lessonId, pageId);
        return ApiResponse.ok(courseware.publicConfig(pageId));
    }

    @GetMapping("/courseware/{packageId}/**")
    public ResponseEntity<Resource> file(@PathVariable String packageId, HttpServletRequest req) throws UnsupportedEncodingException {
        String prefix = "/courseware/" + packageId + "/";
        String uri = req.getRequestURI().substring(req.getContextPath().length());
        if (!uri.startsWith(prefix)) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        String rel = URLDecoder.decode(uri.substring(prefix.length()).replace("+", "%2B"), "UTF-8");
        Path f = courseware.resolve(packageId, rel);
        if (f == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        MediaType type = MediaTypeFactory.getMediaType(f.getFileName().toString()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES))
                .header("X-Content-Type-Options", "nosniff");
        if (!courseware.isCompat(packageId)) {
            b.header("Content-Security-Policy", SANDBOX).header("Access-Control-Allow-Origin", "*");
        }
        return b.body(new FileSystemResource(f));
    }
}
