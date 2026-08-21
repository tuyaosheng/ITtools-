package com.ittools.platform.web;

import com.ittools.platform.service.ImportService;
import com.ittools.platform.service.dto.ImportResult;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;

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
        byte[] xlsx = buildTemplate(
                new String[]{"姓名", "学号", "学籍号", "年级码", "班级", "初始密码"},
                new String[]{"张三", "001", "2024001", "2024", "1班", ""});
        return download(xlsx, "students_template.xlsx");
    }

    @GetMapping("/teachers/template")
    public ResponseEntity<ByteArrayResource> teacherTemplate() {
        byte[] xlsx = buildTemplate(
                new String[]{"姓名", "登录名", "初始密码"},
                new String[]{"王老师", "teacher01", ""});
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

    private byte[] buildTemplate(String[] header, String[] example) {
        try {
            Workbook wb = new XSSFWorkbook();
            try {
                Sheet sheet = wb.createSheet("模板");
                Row h = sheet.createRow(0);
                for (int c = 0; c < header.length; c++) {
                    h.createCell(c).setCellValue(header[c]);
                    sheet.setColumnWidth(c, 4000);
                }
                Row ex = sheet.createRow(1);
                for (int c = 0; c < example.length; c++) {
                    ex.createCell(c).setCellValue(example[c]);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                wb.write(out);
                return out.toByteArray();
            } finally {
                wb.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("生成模板失败：" + e.getMessage());
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
