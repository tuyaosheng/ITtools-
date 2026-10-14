package com.ittools.homework.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ittools.classroom.service.ClassBindingService;
import com.ittools.classroom.service.LessonService;
import com.ittools.grading.service.GradingService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.ittools.platform.db.Sql.p;

/**
 * 课后操作题（原 aftercourseword/excel/pptstu 等）与操作题报表。
 * 教师在作品提交页勾选“课后练习”后，上过该课程的班级的学生可以在课后下载素材、提交作品并立即得到自动评分，可反复提交，保留最好成绩。
 */
@Service
public class OpPracticeService {
    private final NamedParameterJdbcTemplate jdbc;
    private final HomeworkService homework;
    private final GradingService grading;
    private final FileStorageService storage;
    private final LessonService lessons;
    private final ClassBindingService bindings;
    private final ObjectMapper json = new ObjectMapper();

    public OpPracticeService(NamedParameterJdbcTemplate jdbc, HomeworkService homework, GradingService grading, FileStorageService storage,
                             LessonService lessons, ClassBindingService bindings) {
        this.jdbc = jdbc;
        this.homework = homework;
        this.grading = grading;
        this.storage = storage;
        this.lessons = lessons;
        this.bindings = bindings;
    }

    private static final String VISIBLE = "hc.practice AND pg.page_type = 'HOMEWORK' AND EXISTS (SELECT 1 FROM lesson l "
            + "WHERE l.course_id = pg.course_id AND l.class_id = (SELECT class_id FROM users WHERE id = :s))";

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long studentId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT pg.id AS \"pageId\", pg.title, c.name AS \"courseName\", t.name AS \"teacherName\", "
                + "pg.file_name AS \"materialName\", hc.auto_visibility AS vis, o.best_score AS \"bestScore\", o.score, o.max_score AS \"maxScore\", "
                + "o.attempts, o.submitted_at AS \"submittedAt\" FROM course_page pg JOIN homework_config hc ON hc.page_id = pg.id "
                + "JOIN course c ON c.id = pg.course_id JOIN users t ON t.id = c.teacher_id "
                + "LEFT JOIN op_practice o ON o.page_id = pg.id AND o.student_id = :s WHERE " + VISIBLE + " ORDER BY c.name, pg.seq", p("s", studentId));
        for (Map<String, Object> m : l) {
            if ("NONE".equals(m.remove("vis"))) {
                m.put("bestScore", null);
                m.put("score", null);
                m.put("maxScore", null);
            }
        }
        return l;
    }

    private Map<String, Object> requireVisible(Long studentId, Long pageId) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT pg.id, pg.title, pg.content, pg.file_name AS \"materialName\", c.name AS \"courseName\" "
                + "FROM course_page pg JOIN homework_config hc ON hc.page_id = pg.id JOIN course c ON c.id = pg.course_id "
                + "WHERE pg.id = :p AND " + VISIBLE, p("p", pageId, "s", studentId));
        if (l.isEmpty()) throw new IllegalArgumentException("该课后操作题未开放");
        return l.get(0);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> view(Long studentId, Long pageId) {
        Map<String, Object> out = new LinkedHashMap<>(requireVisible(studentId, pageId));
        Map<String, Object> cfg = homework.config(pageId);
        out.put("allowedExts", cfg.get("allowedExts"));
        List<Map<String, Object>> mine = jdbc.queryForList("SELECT file_name AS \"fileName\", score AS \"autoScore\", max_score AS \"autoMax\", "
                + "detail AS \"autoDetail\", best_score AS \"bestScore\", attempts, submitted_at AS \"submittedAt\" "
                + "FROM op_practice WHERE page_id = :p AND student_id = :s", p("p", pageId, "s", studentId));
        Map<String, Object> m = mine.isEmpty() ? null : mine.get(0);
        if (m != null) {
            String vis = String.valueOf(cfg.get("autoVisibility"));
            HomeworkService.hideAuto(m, vis);
            if ("NONE".equals(vis)) m.put("bestScore", null);
        }
        out.put("mine", m);
        out.put("autoVisibility", cfg.get("autoVisibility"));
        return out;
    }

    @Transactional
    public Map<String, Object> submit(Long studentId, Long pageId, MultipartFile file) {
        requireVisible(studentId, pageId);
        Map<String, Object> cfg = homework.config(pageId);
        String ext = FileStorageService.extensionOf(file.getOriginalFilename());
        String allowed = (String) cfg.get("allowedExts");
        if (allowed != null && !allowed.isEmpty() && !Arrays.asList(allowed.split(",")).contains(ext)) {
            throw new IllegalArgumentException("只允许提交以下类型的文件：" + allowed);
        }
        StoredFile sf = storage.store(file, "practice");
        Map<String, Object> auto = grading.evaluate(pageId, storage.resolve(sf.getRelPath()), sf.getOriginalName());
        Object score = auto == null ? null : auto.get("score");
        List<Map<String, Object>> old = jdbc.queryForList("SELECT id, file_path FROM op_practice WHERE page_id = :p AND student_id = :s",
                p("p", pageId, "s", studentId));
        if (old.isEmpty()) {
            jdbc.update("INSERT INTO op_practice(page_id, student_id, file_path, file_name, file_size, score, max_score, detail, best_score) "
                            + "VALUES (:p, :s, :fp, :fn, :sz, :sc, :mx, :d, :sc)",
                    p("p", pageId, "s", studentId, "fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize(),
                            "sc", score, "mx", auto == null ? null : auto.get("max"), "d", auto == null ? null : auto.get("detail")));
        } else {
            storage.delete((String) old.get(0).get("file_path"));
            jdbc.update("UPDATE op_practice SET file_path = :fp, file_name = :fn, file_size = :sz, score = :sc, max_score = :mx, detail = :d, "
                            + "best_score = GREATEST(COALESCE(best_score, 0), COALESCE(CAST(:sc AS NUMERIC), 0)), attempts = attempts + 1, submitted_at = now() WHERE id = :id",
                    p("fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize(), "sc", score, "mx", auto == null ? null : auto.get("max"),
                            "d", auto == null ? null : auto.get("detail"), "id", old.get(0).get("id")));
        }
        return view(studentId, pageId);
    }

    // ================= 教师：操作题报表 =================

    /** 课后操作题报表：某班学生的练习次数、最近/最好成绩，以及各评分点通过率。 */
    @Transactional(readOnly = true)
    public Map<String, Object> practiceReport(Long teacherId, Long pageId, Long classId) {
        grading.requirePageOwner(teacherId, pageId);
        if (!bindings.teacherCanUseClass(teacherId, classId)) throw new IllegalArgumentException("无权查看该班级");
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT u.id AS \"studentId\", u.student_no AS \"studentNo\", u.name, o.attempts, "
                + "o.score, o.max_score AS \"maxScore\", o.best_score AS \"bestScore\", o.submitted_at AS \"submittedAt\", o.detail "
                + "FROM users u LEFT JOIN op_practice o ON o.student_id = u.id AND o.page_id = :p "
                + "WHERE u.class_id = :k AND u.role = 'STUDENT' AND NOT u.graduated ORDER BY u.student_no NULLS LAST, u.name", p("p", pageId, "k", classId));
        List<String> details = new ArrayList<>();
        for (Map<String, Object> r : rows) {
            Object d = r.remove("detail");
            if (d != null) details.add((String) d);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("students", rows);
        out.put("rules", ruleStats(details));
        return out;
    }

    /** 课堂中某作品页的评分点通过率（哪些操作学生最容易出错）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lessonRuleStats(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        return ruleStats(jdbc.queryForList("SELECT auto_detail FROM submission WHERE lesson_id = :l AND page_id = :p AND auto_detail IS NOT NULL",
                p("l", lessonId, "p", pageId), String.class));
    }

    List<Map<String, Object>> ruleStats(List<String> details) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (String d : details) {
            List<Map<String, Object>> items;
            try {
                items = json.readValue(d, new TypeReference<List<Map<String, Object>>>() { });
            } catch (IOException e) {
                continue;
            }
            for (int i = 0; i < items.size(); i++) {
                Map<String, Object> it = items.get(i);
                while (out.size() <= i) {
                    Map<String, Object> s = new LinkedHashMap<>();
                    s.put("seq", out.size() + 1);
                    s.put("total", 0);
                    s.put("passed", 0);
                    out.add(s);
                }
                Map<String, Object> s = out.get(i);
                s.putIfAbsent("name", it.get("name"));
                if (it.get("note") != null) s.putIfAbsent("note", it.get("note"));
                s.put("total", (Integer) s.get("total") + 1);
                if (Boolean.TRUE.equals(it.get("passed"))) s.put("passed", (Integer) s.get("passed") + 1);
            }
        }
        for (Map<String, Object> s : out) {
            int t = (Integer) s.get("total");
            s.put("rate", t == 0 ? null : Math.round((Integer) s.get("passed") * 1000.0 / t) / 10.0);
        }
        return out;
    }

    /** 教师可用于课后练习报表的作品页（自己课程中开启了课后练习的）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> teacherPages(Long teacherId) {
        return jdbc.queryForList("SELECT pg.id, pg.title, c.name AS \"courseName\", "
                + "(SELECT COUNT(*) FROM op_practice o WHERE o.page_id = pg.id) AS submissions FROM course_page pg "
                + "JOIN homework_config hc ON hc.page_id = pg.id JOIN course c ON c.id = pg.course_id "
                + "WHERE hc.practice AND c.teacher_id = :t ORDER BY c.name, pg.seq", p("t", teacherId));
    }
}
