package com.ittools.platform.web;

import com.ittools.platform.service.ImportService;
import com.ittools.platform.service.dto.ImportResult;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;


/**
 * Batch import of students / teachers from an uploaded .xlsx/.xls/.csv file,
 * plus downloadable .xlsx templates. ADMIN-only (SecurityConfig guards /api/admin/**).
 */
@RestController
@RequestMapping("/api/admin/import")
public class AdminImportController {

    private static final String XLSX_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ImportService importService;

    public AdminImportController(ImportService importService) {
        this.importService = importService;
    }

    @PostMapping("/students")
    public ApiResponse<ImportResult> importStudents(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importStudents(openStream(file), file.getOriginalFilename()));
    }

    @PostMapping("/teachers")
    public ApiResponse<ImportResult> importTeachers(@RequestParam("file") MultipartFile file) {
        return ApiResponse.ok(importService.importTeachers(openStream(file), file.getOriginalFilename()));
    }

    @GetMapping("/students/template")
    public ResponseEntity<ByteArrayResource> studentTemplate() {
        byte[] xlsx = com.ittools.platform.service.ImportTemplates.build("学生",
                new String[]{"姓名", "学号", "学籍号", "年级码", "班级", "初始密码"},
                new String[][]{{"张三", "01", "2024001", "2024", "1班", ""}, {"李四", "02", "2024002", "2024", "1班", ""}},
                new int[]{12, 8, 16, 10, 10, 12},
                "必填：姓名、年级码、班级。年级码是入学年份（如 2024），班级写班名（如 1班、高一(1)班）；年级和班级不存在会自动创建。",
                "学号是班内座号，可空；学籍号全校唯一，可空，填写后学生也能用学籍号登录。",
                "初始密码可空，空着默认 123456。同一个班里不能有同名学生。");
        return download(xlsx, "students_template.xlsx");
    }

    @GetMapping("/teachers/template")
    public ResponseEntity<ByteArrayResource> teacherTemplate() {
        byte[] xlsx = com.ittools.platform.service.ImportTemplates.build("教师",
                new String[]{"姓名", "登录名", "初始密码"},
                new String[][]{{"王老师", "teacher01", ""}},
                new int[]{12, 16, 12},
                "必填：姓名、登录名。登录名用于教师登录，不能与已有教师重复，建议用字母和数字。",
                "初始密码可空，空着默认 123456。");
        return download(xlsx, "teachers_template.xlsx");
    }

    // ---- helpers ----------------------------------------------------------

    private java.io.InputStream openStream(MultipartFile file) {
        try {
            return file.getInputStream();
        } catch (Exception e) {
            throw new IllegalArgumentException("无法读取上传文件：" + e.getMessage());
        }
    }

    private ResponseEntity<ByteArrayResource> download(byte[] bytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(XLSX_TYPE))
                .contentLength(bytes.length)
                .body(new ByteArrayResource(bytes));
    }
}
