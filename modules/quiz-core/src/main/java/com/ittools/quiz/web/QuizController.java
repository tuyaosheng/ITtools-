package com.ittools.quiz.web;

import com.ittools.classroom.service.CourseService;
import com.ittools.classroom.service.LessonService;
import com.ittools.platform.security.CurrentUser;
import com.ittools.platform.service.dto.ImportResult;
import com.ittools.platform.web.ApiResponse;
import com.ittools.platform.web.Body;
import com.ittools.quiz.service.PaperService;
import com.ittools.quiz.service.QuestionService;
import com.ittools.quiz.service.TestService;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class QuizController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final QuestionService questions;
    private final PaperService papers;
    private final TestService tests;
    private final LessonService lessons;
    private final CourseService courses;

    public QuizController(QuestionService questions, PaperService papers, TestService tests, LessonService lessons, CourseService courses) {
        this.questions = questions;
        this.papers = papers;
        this.tests = tests;
        this.lessons = lessons;
        this.courses = courses;
    }

    // ---- 题库 ----
    @GetMapping("/api/teacher/quiz/questions")
    public ApiResponse<List<Map<String, Object>>> questions(@RequestParam(required = false) String category,
                                                            @RequestParam(required = false) String qtype,
                                                            @RequestParam(required = false) String keyword,
                                                            @RequestParam(defaultValue = "true") boolean includeShared,
                                                            @RequestParam(required = false) String tag,
                                                            @RequestParam(required = false) Integer minStars,
                                                            @RequestParam(defaultValue = "active") String status) {
        return ApiResponse.ok(questions.list(CurrentUser.id(), category, qtype, keyword, includeShared, tag, minStars, status));
    }

    @GetMapping("/api/teacher/quiz/categories")
    public ApiResponse<List<String>> categories() {
        return ApiResponse.ok(questions.categories(CurrentUser.id()));
    }

    @PostMapping("/api/teacher/quiz/questions")
    public ApiResponse<Map<String, Object>> createQuestion(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(questions.save(CurrentUser.id(), null, body));
    }

    @PutMapping("/api/teacher/quiz/questions/{id}")
    public ApiResponse<Map<String, Object>> updateQuestion(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(questions.save(CurrentUser.id(), id, body));
    }

    @DeleteMapping("/api/teacher/quiz/questions/{id}")
    public ApiResponse<Void> deleteQuestion(@PathVariable Long id) {
        questions.delete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/api/teacher/quiz/questions/import")
    public ApiResponse<ImportResult> importQuestions(@RequestParam("file") MultipartFile file) throws IOException {
        return ApiResponse.ok(questions.importFile(CurrentUser.id(), file.getInputStream(), file.getOriginalFilename()));
    }

    @GetMapping("/api/teacher/quiz/questions/template")
    public ResponseEntity<byte[]> template() {
        return xlsx("题库导入模板.xlsx", questions.template());
    }

    // ---- 试卷 ----
    @GetMapping("/api/teacher/quiz/papers")
    public ApiResponse<List<Map<String, Object>>> papers() {
        return ApiResponse.ok(papers.list(CurrentUser.id()));
    }

    @GetMapping("/api/teacher/quiz/papers/{id}")
    public ApiResponse<Map<String, Object>> paper(@PathVariable Long id) {
        papers.requireReadable(CurrentUser.id(), id);
        return ApiResponse.ok(papers.get(id));
    }

    @PostMapping("/api/teacher/quiz/papers")
    public ApiResponse<Map<String, Object>> createPaper(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(papers.save(CurrentUser.id(), null, b.str("name"), b.str("description"), b.bool("shared", false), b.maps("items")));
    }

    @PutMapping("/api/teacher/quiz/papers/{id}")
    public ApiResponse<Map<String, Object>> updatePaper(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(papers.save(CurrentUser.id(), id, b.str("name"), b.str("description"), b.bool("shared", false), b.maps("items")));
    }

    @PostMapping("/api/teacher/quiz/papers/{id}/random")
    public ApiResponse<Map<String, Object>> random(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        Map<String, Integer> counts = new HashMap<>();
        for (String t : QuestionService.TYPES) counts.put(t, b.intv(t, 0));
        return ApiResponse.ok(papers.randomFill(CurrentUser.id(), id, b.str("category"), counts, b.str("tag"), b.intv("minStars")));
    }

    @DeleteMapping("/api/teacher/quiz/papers/{id}")
    public ApiResponse<Void> deletePaper(@PathVariable Long id) {
        papers.delete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    // ---- 测验 ----
    @PostMapping("/api/teacher/quiz/tests")
    public ApiResponse<List<Map<String, Object>>> publish(@RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        List<Long> classIds = b.longs("classIds");
        if (classIds.isEmpty() && b.lng("classId") != null) classIds.add(b.lng("classId"));
        Integer tl = b.intv("timeLimit");
        return ApiResponse.ok(tests.publish(CurrentUser.id(), b.reqLng("paperId", "试卷"), classIds, b.str("title"),
                b.bool("showAnswer", true), tl == null || tl <= 0 ? null : tl, b.str("kind"), b.str("mode")));
    }

    @GetMapping("/api/teacher/quiz/tests")
    public ApiResponse<List<Map<String, Object>>> teacherTests(@RequestParam(required = false) String kind) {
        return ApiResponse.ok(tests.teacherTests(CurrentUser.id(), kind));
    }

    @PostMapping("/api/teacher/quiz/tests/{id}/finalize")
    public ApiResponse<Map<String, Object>> finalizeTest(@PathVariable Long id) {
        return ApiResponse.ok(tests.finalizeTest(CurrentUser.id(), id));
    }

    @PostMapping("/api/teacher/quiz/attempts/{id}/return")
    public ApiResponse<Void> returnAttempt(@PathVariable Long id, @RequestBody(required = false) Map<String, Object> body) {
        tests.returnAttempt(CurrentUser.id(), id, Body.of(body).str("note"));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/teacher/quiz/tests/{id}/fill-groups")
    public ApiResponse<List<Map<String, Object>>> fillGroups(@PathVariable Long id) {
        return ApiResponse.ok(tests.fillGroups(CurrentUser.id(), id));
    }

    @PutMapping("/api/teacher/quiz/tests/{id}/fill-groups")
    public ApiResponse<Integer> gradeFillGroup(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        Double score = b.dbl("score");
        if (score == null) throw new IllegalArgumentException("分数不能为空");
        Object answer = body.get("answer");
        return ApiResponse.ok(tests.gradeFillGroup(CurrentUser.id(), id, b.reqLng("questionId", "题目"), answer == null ? null : String.valueOf(answer), score));
    }

    @GetMapping("/api/teacher/quiz/tests/{id}/grade-stats")
    public ApiResponse<Map<String, Object>> gradeStats(@PathVariable Long id) {
        tests.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(tests.gradeStats(id));
    }

    @GetMapping("/api/teacher/quiz/pages/{pageId}/config")
    public ApiResponse<Map<String, Object>> pageConfig(@PathVariable Long pageId) {
        return ApiResponse.ok(tests.pageConfig(pageId));
    }

    @PutMapping("/api/teacher/quiz/pages/{pageId}/config")
    public ApiResponse<Map<String, Object>> savePageConfig(@PathVariable Long pageId, @RequestBody Map<String, Object> body) {
        Map<String, Object> page = courses.page(pageId);
        courses.requireOwner(CurrentUser.id(), ((Number) page.get("courseId")).longValue());
        Body b = Body.of(body);
        return ApiResponse.ok(tests.savePageConfig(pageId, b.reqStr("mode", "考试方式"), b.intv("timeLimit"), b.bool("showAnswer", true)));
    }

    @GetMapping("/api/teacher/quiz/tests/{id}")
    public ApiResponse<Map<String, Object>> test(@PathVariable Long id) {
        Map<String, Object> t = new HashMap<>(tests.requireTeacher(CurrentUser.id(), id));
        t.put("results", tests.results(id));
        return ApiResponse.ok(t);
    }

    @GetMapping("/api/teacher/quiz/lessons/{lessonId}/pages/{pageId}/test")
    public ApiResponse<Map<String, Object>> lessonTest(@PathVariable Long lessonId, @PathVariable Long pageId) {
        lessons.requireTeacher(CurrentUser.id(), lessonId);
        Map<String, Object> t = new HashMap<>(tests.lessonPageTest(lessonId, pageId));
        t.put("results", tests.results(((Number) t.get("id")).longValue()));
        return ApiResponse.ok(t);
    }

    @PutMapping("/api/teacher/quiz/tests/{id}/status")
    public ApiResponse<Map<String, Object>> status(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(tests.setStatus(CurrentUser.id(), id, b.str("status"), b.has("showAnswer") ? b.bool("showAnswer", true) : null));
    }

    @DeleteMapping("/api/teacher/quiz/tests/{id}")
    public ApiResponse<Void> deleteTest(@PathVariable Long id) {
        tests.delete(CurrentUser.id(), id);
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/teacher/quiz/tests/{id}/analysis")
    public ApiResponse<List<Map<String, Object>>> analysis(@PathVariable Long id) {
        tests.requireTeacher(CurrentUser.id(), id);
        return ApiResponse.ok(tests.analysis(id));
    }

    @GetMapping("/api/teacher/quiz/attempts/{id}")
    public ApiResponse<Map<String, Object>> attempt(@PathVariable Long id) {
        Map<String, Object> a = tests.attemptDetail(id);
        tests.requireTeacher(CurrentUser.id(), ((Number) a.get("testId")).longValue());
        return ApiResponse.ok(a);
    }

    @PutMapping("/api/teacher/quiz/attempts/{id}/questions/{questionId}")
    public ApiResponse<Map<String, Object>> adjust(@PathVariable Long id, @PathVariable Long questionId, @RequestBody Map<String, Object> body) {
        Double s = Body.of(body).dbl("score");
        if (s == null) throw new IllegalArgumentException("分数不能为空");
        return ApiResponse.ok(tests.adjust(CurrentUser.id(), id, questionId, s));
    }

    @GetMapping("/api/teacher/quiz/tests/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        tests.requireTeacher(CurrentUser.id(), id);
        return xlsx("测验成绩_" + id + ".xlsx", tests.export(id));
    }

    // ---- 学生 ----
    @GetMapping("/api/student/quiz/tests")
    public ApiResponse<List<Map<String, Object>>> studentTests(@RequestParam(required = false) String kind) {
        return ApiResponse.ok(tests.studentTests(CurrentUser.id(), kind));
    }

    @GetMapping("/api/student/quiz/tests/{id}")
    public ApiResponse<Map<String, Object>> open(@PathVariable Long id) {
        return ApiResponse.ok(tests.studentOpen(CurrentUser.id(), id));
    }

    @GetMapping("/api/student/quiz/lessons/{lessonId}/pages/{pageId}")
    public ApiResponse<Map<String, Object>> openLessonQuiz(@PathVariable Long lessonId, @PathVariable Long pageId) {
        lessons.studentPage(CurrentUser.id(), lessonId, pageId);
        Map<String, Object> t = tests.lessonPageTest(lessonId, pageId);
        return ApiResponse.ok(tests.studentOpen(CurrentUser.id(), ((Number) t.get("id")).longValue()));
    }

    @PutMapping("/api/student/quiz/tests/{id}")
    public ApiResponse<Map<String, Object>> save(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Body b = Body.of(body);
        return ApiResponse.ok(tests.studentSave(CurrentUser.id(), id, b.maps("answers"), b.bool("submit", false)));
    }

    @GetMapping("/api/student/quiz/wrong-book")
    public ApiResponse<List<Map<String, Object>>> wrongBook(@RequestParam(required = false) String kind,
                                                            @RequestParam(defaultValue = "false") boolean includeMastered) {
        return ApiResponse.ok(tests.wrongBook(CurrentUser.id(), kind, includeMastered));
    }

    @PutMapping("/api/student/quiz/wrong-book/{questionId}")
    public ApiResponse<Void> mastered(@PathVariable Long questionId, @RequestBody Map<String, Object> body) {
        tests.setMastered(CurrentUser.id(), questionId, Body.of(body).bool("mastered", true));
        return ApiResponse.ok(null);
    }

    @GetMapping("/api/student/quiz/practice/next")
    public ApiResponse<Map<String, Object>> practiceNext(@RequestParam(required = false) String category,
                                                         @RequestParam(required = false) String qtype) {
        return ApiResponse.ok(tests.practiceNext(category, qtype));
    }

    @PostMapping("/api/student/quiz/practice/{questionId}")
    public ApiResponse<Map<String, Object>> practiceAnswer(@PathVariable Long questionId, @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(tests.practiceAnswer(CurrentUser.id(), questionId, body.get("answer")));
    }

    @GetMapping("/api/student/quiz/practice/meta")
    public ApiResponse<Map<String, Object>> practiceMeta() {
        Map<String, Object> m = new HashMap<>();
        m.put("categories", tests.practiceCategories());
        m.put("stats", tests.practiceStats(CurrentUser.id()));
        return ApiResponse.ok(m);
    }

    private static ResponseEntity<byte[]> xlsx(String name, byte[] body) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(XLSX).body(body);
    }
}
