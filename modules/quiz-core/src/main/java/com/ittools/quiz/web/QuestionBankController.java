package com.ittools.quiz.web;

import com.ittools.platform.security.AppUserDetails;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.quiz.service.QuestionBankService;
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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 图片式题库相关接口（题干图片、批量传图、标签、回收站、统计、题库包）。 */
@RestController
public class QuestionBankController {
    private final QuestionBankService bank;
    private final FileStorageService storage;

    public QuestionBankController(QuestionBankService bank, FileStorageService storage) {
        this.bank = bank;
        this.storage = storage;
    }

    @GetMapping("/api/quiz/questions/{id}/image")
    public ResponseEntity<Resource> image(@PathVariable Long id) {
        AppUserDetails u = CurrentUser.get();
        Map<String, Object> q = bank.imageFor(u.userId(), u.role().name(), id);
        String path = (String) q.get("image_path");
        return storage.download(path, "question-" + id + "." + FileStorageService.extensionOf(path), true);
    }

    @PostMapping("/api/teacher/quiz/questions/{id}/image")
    public ApiResponse<Map<String, Object>> setImage(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(bank.setImage(CurrentUser.id(), id, file));
    }

    @DeleteMapping("/api/teacher/quiz/questions/{id}/image")
    public ApiResponse<Map<String, Object>> removeImage(@PathVariable Long id) {
        return ApiResponse.ok(bank.removeImage(CurrentUser.id(), id));
    }

    @PostMapping("/api/teacher/quiz/questions/import-images")
    public ApiResponse<Map<String, Object>> importImages(@RequestParam("file") MultipartFile file,
                                                         @RequestParam String qtype,
                                                         @RequestParam(defaultValue = "4") int optionCount,
                                                         @RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String tags,
                                                         @RequestParam(defaultValue = "5") int score) throws IOException {
        return ApiResponse.ok(bank.importImages(CurrentUser.id(), file, qtype, optionCount, category, splitTags(tags), score));
    }

    @GetMapping("/api/teacher/quiz/tags")
    public ApiResponse<List<Map<String, Object>>> tags() {
        return ApiResponse.ok(bank.tags(CurrentUser.id()));
    }

    @PutMapping("/api/teacher/quiz/tags/rename")
    public ApiResponse<Void> renameTag(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        bank.renameTag(CurrentUser.id(), b.reqStr("from", "原标签"), b.str("to"));
        return ApiResponse.ok(null);
    }

    @DeleteMapping("/api/teacher/quiz/tags")
    public ApiResponse<Void> deleteTag(@RequestParam String tag) {
        bank.deleteTag(CurrentUser.id(), tag);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/quiz/questions/batch")
    public ApiResponse<Integer> batch(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(bank.batch(CurrentUser.id(), Body.of(body).longs("ids"), body));
    }

    @PostMapping("/api/teacher/quiz/questions/restore")
    public ApiResponse<Integer> restore(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(bank.restore(CurrentUser.id(), Body.of(body).longs("ids")));
    }

    @PostMapping("/api/teacher/quiz/questions/purge")
    public ApiResponse<Map<String, Object>> purge(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(bank.purge(CurrentUser.id(), Body.of(body).longs("ids")));
    }

    @GetMapping("/api/teacher/quiz/stats")
    public ApiResponse<Map<String, Object>> stats() {
        return ApiResponse.ok(bank.stats(CurrentUser.id()));
    }

    @PostMapping("/api/teacher/quiz/questions/export")
    public ResponseEntity<byte[]> export(@RequestBody Map<String, Object> body) throws IOException {
        byte[] zip = bank.exportPack(CurrentUser.id(), Body.of(body).longs("ids"));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("题库包_" + LocalDate.now() + ".zip", StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(zip);
    }

    @PostMapping("/api/teacher/quiz/questions/import-pack")
    public ApiResponse<Map<String, Object>> importPack(@RequestParam("file") MultipartFile file,
                                                       @RequestParam(defaultValue = "NONE") String overwrite) throws IOException {
        return ApiResponse.ok(bank.importPack(CurrentUser.id(), file, overwrite));
    }

    private static List<String> splitTags(String s) {
        List<String> out = new ArrayList<>();
        if (s == null) return out;
        for (String t : s.split("[,，;；\\s]+")) if (!t.trim().isEmpty()) out.add(t.trim());
        return out;
    }
}
