package com.ittools.homework.web;

import com.ittools.homework.service.ClassReportService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.web.ApiResponse;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 班级学习情况综合查询、期末统计与学生个人学情。权重 w 依次为 出席,作品,操作题,课后作业,测验。 */
@RestController
public class ClassReportController {
    private static final double[] DEFAULT_WEIGHTS = {10, 30, 20, 20, 20};
    private final ClassReportService reports;

    public ClassReportController(ClassReportService reports) {
        this.reports = reports;
    }

    static double[] weights(String w) {
        if (w == null || w.trim().isEmpty()) return DEFAULT_WEIGHTS;
        String[] parts = w.split(",");
        if (parts.length != 5) throw new IllegalArgumentException("权重需为 5 项");
        double[] out = new double[5];
        for (int i = 0; i < 5; i++) {
            try {
                out[i] = Double.parseDouble(parts[i].trim());
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("权重格式错误");
            }
            if (out[i] < 0 || out[i] > 100) throw new IllegalArgumentException("权重为 0~100");
        }
        return out;
    }

    @GetMapping("/api/teacher/reports/class/summary")
    public ApiResponse<List<Map<String, Object>>> summary(@RequestParam Long classId, @RequestParam(required = false) Long termId,
                                                          @RequestParam(required = false) String w) {
        return ApiResponse.ok(reports.summary(CurrentUser.id(), classId, termId, weights(w)));
    }

    @GetMapping("/api/teacher/reports/class/detail")
    public ApiResponse<Map<String, Object>> detail(@RequestParam Long classId, @RequestParam(required = false) Long termId, @RequestParam String kind) {
        return ApiResponse.ok(reports.detail(CurrentUser.id(), classId, termId, kind));
    }

    @GetMapping("/api/teacher/reports/class/export")
    public ResponseEntity<byte[]> export(@RequestParam Long classId, @RequestParam(required = false) Long termId,
                                         @RequestParam(required = false) String w) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("班级学习情况_" + classId + ".xlsx", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(reports.export(CurrentUser.id(), classId, termId, weights(w)));
    }

    @GetMapping("/api/teacher/reports/students/{id}")
    public ApiResponse<Map<String, Object>> student(@PathVariable Long id, @RequestParam(required = false) Long termId,
                                                    @RequestParam(required = false) String w) {
        return ApiResponse.ok(reports.studentReport(CurrentUser.id(), id, termId, weights(w)));
    }

    @GetMapping("/api/student/report")
    public ApiResponse<Map<String, Object>> mine(@RequestParam(required = false) Long termId) {
        return ApiResponse.ok(reports.studentReport(null, CurrentUser.id(), termId, DEFAULT_WEIGHTS));
    }
}
