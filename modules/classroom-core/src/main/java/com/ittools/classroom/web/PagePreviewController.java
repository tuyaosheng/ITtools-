package com.ittools.classroom.web;

import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.OfficePreviewService;
import com.ittools.platform.web.ApiResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/** 课程“资料课件”页面的在线预览：PPT 按张出图、旧版 Word 转网页（权限与附件下载相同）。 */
@RestController
public class PagePreviewController {
    /** 转出的 Word 网页只当静态文档显示，不让其中脚本运行。 */
    private static final String DOC_CSP = "sandbox; default-src 'none'; style-src 'unsafe-inline'; img-src data:";

    /** LibreOffice 的播放脚本用 eval 计算动画公式，所以要 unsafe-eval；页面在沙箱里（不同源），拿不到平台登录状态。 */
    private static final String SHOW_CSP = "sandbox allow-scripts; default-src 'none'; script-src 'unsafe-inline' 'unsafe-eval'; "
            + "style-src 'unsafe-inline'; img-src data:; font-src data:";

    private final CourseService courses;
    private final LessonService lessons;
    private final OfficePreviewService previews;

    public PagePreviewController(CourseService courses, LessonService lessons, OfficePreviewService previews) {
        this.courses = courses;
        this.lessons = lessons;
        this.previews = previews;
    }

    @GetMapping("/api/classroom/pages/{pageId}/preview")
    public ApiResponse<Map<String, Object>> info(@PathVariable Long pageId,
                                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String mode) {
        Map<String, Object> pg = readable(pageId);
        return ApiResponse.ok(previews.info((String) pg.get("filePath"), (String) pg.get("fileName"), mode));
    }

    /** 带动画的 PPT 演示：作为沙箱文档运行（不同源、拿不到平台登录状态），只允许它自带的播放脚本。 */
    @GetMapping("/api/classroom/pages/{pageId}/preview/show")
    public ResponseEntity<Resource> show(@PathVariable Long pageId) {
        Map<String, Object> pg = readable(pageId);
        return ResponseEntity.ok().contentType(MediaType.valueOf("application/xhtml+xml;charset=UTF-8"))
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .header("Content-Security-Policy", SHOW_CSP)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(previews.show((String) pg.get("filePath"))));
    }

    @GetMapping("/api/classroom/pages/{pageId}/preview/slides/{n}")
    public ResponseEntity<Resource> slide(@PathVariable Long pageId, @PathVariable int n) {
        Map<String, Object> pg = readable(pageId);
        return ResponseEntity.ok().contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(10, TimeUnit.MINUTES).cachePrivate())
                .body(new FileSystemResource(previews.slide((String) pg.get("filePath"), n)));
    }

    @GetMapping("/api/classroom/pages/{pageId}/preview/doc")
    public ResponseEntity<Resource> doc(@PathVariable Long pageId) {
        Map<String, Object> pg = readable(pageId);
        return ResponseEntity.ok().contentType(new MediaType("text", "html", java.nio.charset.StandardCharsets.UTF_8))
                .header("Content-Security-Policy", DOC_CSP)
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(previews.docHtml((String) pg.get("filePath"))));
    }

    private Map<String, Object> readable(Long pageId) {
        Map<String, Object> pg = courses.page(pageId);
        AppUserDetails u = CurrentUser.get();
        if (!lessons.canReadCourse(u.userId(), u.role().name(), ((Number) pg.get("courseId")).longValue())) {
            throw new IllegalArgumentException("无权查看该文件");
        }
        if (pg.get("filePath") == null) throw new IllegalArgumentException("该页面没有附件");
        return pg;
    }
}
