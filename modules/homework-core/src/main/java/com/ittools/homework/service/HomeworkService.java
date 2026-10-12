package com.ittools.homework.service;

import com.ittools.classroom.service.LessonService;
import com.ittools.grading.service.GradingService;
import com.ittools.lab.service.LabQueryService;
import com.ittools.platform.settings.SystemSettingService;
import com.ittools.platform.storage.FileStorageService;
import com.ittools.platform.storage.StoredFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.ittools.platform.db.Sql.p;

/**
 * 子项目 3：作品提交与展示（原 courseuploadfile / markstufile / stufilepack / Essencestufile / 互评）。
 */
@Service
public class HomeworkService {
    private final NamedParameterJdbcTemplate jdbc;
    private final LessonService lessons;
    private final GradingService grading;
    private final FileStorageService storage;
    private final SystemSettingService settings;
    private final LabQueryService lab;
    private final ObjectMapper json = new ObjectMapper();

    public HomeworkService(NamedParameterJdbcTemplate jdbc, LessonService lessons, GradingService grading,
                           FileStorageService storage, SystemSettingService settings, LabQueryService lab) {
        this.jdbc = jdbc;
        this.lessons = lessons;
        this.grading = grading;
        this.storage = storage;
        this.settings = settings;
        this.lab = lab;
    }

    // ================= 作品页设置 =================

    @Transactional(readOnly = true)
    public Map<String, Object> config(Long pageId) {
        List<Map<String, Object>> l = jdbc.queryForList(
                "SELECT page_id AS \"pageId\", allowed_exts AS \"allowedExts\", peer_review AS \"peerReview\", "
                        + "show_classmates AS \"showClassmates\", auto_grade AS \"autoGrade\", rubric_id AS \"rubricId\" "
                        + "FROM homework_config WHERE page_id = :p", p("p", pageId));
        if (!l.isEmpty()) return l.get(0);
        Map<String, Object> d = new HashMap<>();
        d.put("pageId", pageId);
        d.put("allowedExts", "");
        d.put("peerReview", false);
        d.put("showClassmates", false);
        d.put("autoGrade", true);
        d.put("rubricId", null);
        return d;
    }

    @Transactional
    public Map<String, Object> saveConfig(Long teacherId, Long pageId, Map<String, Object> f) {
        grading.requirePageOwner(teacherId, pageId);
        Object rubric = f.get("rubricId");
        jdbc.update("INSERT INTO homework_config(page_id, allowed_exts, peer_review, show_classmates, auto_grade, rubric_id) "
                        + "VALUES (:p, :e, :pr, :sc, :ag, :r) ON CONFLICT (page_id) DO UPDATE SET allowed_exts = EXCLUDED.allowed_exts, "
                        + "peer_review = EXCLUDED.peer_review, show_classmates = EXCLUDED.show_classmates, "
                        + "auto_grade = EXCLUDED.auto_grade, rubric_id = EXCLUDED.rubric_id",
                p("p", pageId, "e", normalizeExts((String) f.get("allowedExts")), "pr", Boolean.TRUE.equals(f.get("peerReview")),
                        "sc", Boolean.TRUE.equals(f.get("showClassmates")), "ag", !Boolean.FALSE.equals(f.get("autoGrade")),
                        "r", rubric == null ? null : ((Number) rubric).longValue()));
        return config(pageId);
    }

    // ================= 学生 =================

    private static final String SUB_COLS =
            "s.id, s.lesson_id AS \"lessonId\", s.page_id AS \"pageId\", s.student_id AS \"studentId\", u.name AS \"studentName\", "
                    + "u.student_no AS \"studentNo\", s.file_name AS \"fileName\", s.file_size AS \"fileSize\", s.description, "
                    + "s.pc_name AS \"pcName\", s.submitted_at AS \"submittedAt\", s.submit_count AS \"submitCount\", s.late, "
                    + "s.auto_score AS \"autoScore\", s.auto_max AS \"autoMax\", s.auto_detail AS \"autoDetail\", "
                    + "s.teacher_score AS \"teacherScore\", s.rubric_scores AS \"rubricScores\", s.title, s.self_score AS \"selfScore\", s.rating, s.returned, s.return_note AS \"returnNote\", s.teacher_comment AS \"teacherComment\", "
                    + "s.graded_at AS \"gradedAt\", s.essence, "
                    + "(SELECT ROUND(AVG(pr.score), 1) FROM peer_review pr WHERE pr.submission_id = s.id) AS \"peerAvg\", "
                    + "(SELECT COUNT(*) FROM peer_review pr WHERE pr.submission_id = s.id) AS \"peerCount\" ";

    @Transactional(readOnly = true)
    public Map<String, Object> studentView(Long studentId, Long lessonId, Long pageId) {
        lessons.requireStudent(studentId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        Map<String, Object> out = new HashMap<>();
        out.put("config", config(pageId));
        List<Map<String, Object>> mine = jdbc.queryForList("SELECT " + SUB_COLS + "FROM submission s JOIN users u ON u.id = s.student_id "
                + "WHERE s.lesson_id = :l AND s.page_id = :p AND s.student_id = :s", p("l", lessonId, "p", pageId, "s", studentId));
        Map<String, Object> sub = mine.isEmpty() ? null : mine.get(0);
        if (sub != null) {
            sub.put("reviews", jdbc.queryForList(
                    "SELECT pr.score, pr.comment, pr.created_at AS \"createdAt\" FROM peer_review pr WHERE pr.submission_id = :id "
                            + "ORDER BY pr.created_at DESC", p("id", sub.get("id"))));
        }
        out.put("submission", sub);
        Map<String, Object> l = lessons.get(lessonId);
        out.put("canSubmit", "ACTIVE".equals(l.get("status")) || settings.getBool(SystemSettingService.HOMEWORK_ALLOW_LATE)
                || (sub != null && Boolean.TRUE.equals(sub.get("returned"))));
        out.put("rubric", rubricItemsFor(pageId));
        return out;
    }

    @Transactional
    public Map<String, Object> submit(Long studentId, Long lessonId, Long pageId, MultipartFile file, String description, String ip) {
        return submit(studentId, lessonId, pageId, file, description, ip, null, null);
    }

    /** 提交作品（原 courseuploadfile：作品名、自我评分、简介）。重新提交会清除“发回重做”标记。 */
    @Transactional
    public Map<String, Object> submit(Long studentId, Long lessonId, Long pageId, MultipartFile file, String description, String ip,
                                      String title, Integer selfScore) {
        if (title != null && title.length() > 100) throw new IllegalArgumentException("作品名不超过 100 字");
        if (description != null && description.length() > 500) throw new IllegalArgumentException("简介不超过 500 字");
        if (selfScore != null && (selfScore < 0 || selfScore > 100)) throw new IllegalArgumentException("自我评分为 0~100");
        lessons.requireStudent(studentId, lessonId);
        Map<String, Object> page = lessons.studentPage(studentId, lessonId, pageId);
        if (!"HOMEWORK".equals(page.get("pageType"))) throw new IllegalArgumentException("该页面不是作品提交页");
        Map<String, Object> l = lessons.get(lessonId);
        boolean late = !"ACTIVE".equals(l.get("status"));
        boolean returned = Boolean.TRUE.equals(jdbc.queryForList("SELECT returned FROM submission WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId), Boolean.class).stream().findFirst().orElse(false));
        if (late && !returned && !settings.getBool(SystemSettingService.HOMEWORK_ALLOW_LATE)) {
            throw new IllegalArgumentException("课堂已结束，系统未开放作业补交");
        }
        Map<String, Object> cfg = config(pageId);
        String ext = FileStorageService.extensionOf(file.getOriginalFilename());
        String allowed = (String) cfg.get("allowedExts");
        if (allowed != null && !allowed.isEmpty() && !Arrays.asList(allowed.split(",")).contains(ext)) {
            throw new IllegalArgumentException("只允许提交以下类型的文件：" + allowed);
        }
        StoredFile sf = storage.store(file, "homework");
        Map<String, Object> auto = null;
        if (!Boolean.FALSE.equals(cfg.get("autoGrade"))) {
            auto = grading.evaluate(pageId, storage.resolve(sf.getRelPath()), sf.getOriginalName());
        }
        List<Map<String, Object>> old = jdbc.queryForList(
                "SELECT id, file_path FROM submission WHERE lesson_id = :l AND page_id = :p AND student_id = :s",
                p("l", lessonId, "p", pageId, "s", studentId));
        String pc = (String) lab.workstationByIp(ip).get("pcName");
        if (old.isEmpty()) {
            jdbc.update("INSERT INTO submission(lesson_id, page_id, student_id, file_path, file_name, file_size, description, "
                            + "pc_name, ip_address, late, auto_score, auto_max, auto_detail, title, self_score) "
                            + "VALUES (:l, :p, :s, :fp, :fn, :sz, :d, :pc, :ip, :late, :as, :am, :ad, :ti, :ss)",
                    p("l", lessonId, "p", pageId, "s", studentId, "fp", sf.getRelPath(), "fn", sf.getOriginalName(),
                            "sz", sf.getSize(), "d", description, "pc", pc, "ip", ip, "late", late,
                            "as", auto == null ? null : auto.get("score"), "am", auto == null ? null : auto.get("max"),
                            "ad", auto == null ? null : auto.get("detail"), "ti", title, "ss", selfScore));
        } else {
            storage.delete((String) old.get(0).get("file_path"));
            // 重新提交：保留教师评分/精华标记之外的数据全部更新，教师需重新批阅
            jdbc.update("UPDATE submission SET file_path = :fp, file_name = :fn, file_size = :sz, description = :d, pc_name = :pc, "
                            + "ip_address = :ip, submitted_at = now(), submit_count = submit_count + 1, late = :late, "
                            + "auto_score = :as, auto_max = :am, auto_detail = :ad, teacher_score = NULL, rubric_scores = NULL, "
                            + "graded_at = NULL, rating = NULL, returned = FALSE, return_note = NULL, title = :ti, self_score = :ss WHERE id = :id",
                    p("fp", sf.getRelPath(), "fn", sf.getOriginalName(), "sz", sf.getSize(), "d", description, "pc", pc, "ip", ip,
                            "late", late, "as", auto == null ? null : auto.get("score"), "am", auto == null ? null : auto.get("max"),
                            "ad", auto == null ? null : auto.get("detail"), "ti", title, "ss", selfScore, "id", old.get(0).get("id")));
        }
        return studentView(studentId, lessonId, pageId);
    }

    /** 同学作品（开放展示或互评时）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> classmates(Long studentId, Long lessonId, Long pageId) {
        lessons.requireStudent(studentId, lessonId);
        Map<String, Object> cfg = config(pageId);
        if (!Boolean.TRUE.equals(cfg.get("showClassmates")) && !Boolean.TRUE.equals(cfg.get("peerReview"))) {
            throw new IllegalArgumentException("老师未开放查看同学作品");
        }
        return jdbc.queryForList("SELECT s.id, u.name AS \"studentName\", s.file_name AS \"fileName\", s.file_size AS \"fileSize\", "
                        + "s.description, s.submitted_at AS \"submittedAt\", "
                        + "(SELECT ROUND(AVG(pr.score), 1) FROM peer_review pr WHERE pr.submission_id = s.id) AS \"peerAvg\", "
                        + "(SELECT pr.score FROM peer_review pr WHERE pr.submission_id = s.id AND pr.reviewer_id = :me) AS \"myScore\", "
                        + "(SELECT pr.comment FROM peer_review pr WHERE pr.submission_id = s.id AND pr.reviewer_id = :me) AS \"myComment\" "
                        + "FROM submission s JOIN users u ON u.id = s.student_id "
                        + "WHERE s.lesson_id = :l AND s.page_id = :p AND s.student_id <> :me ORDER BY s.submitted_at",
                p("l", lessonId, "p", pageId, "me", studentId));
    }

    @Transactional
    public void peerReview(Long studentId, Long submissionId, int score, String comment) {
        Map<String, Object> s = submission(submissionId);
        Long lessonId = ((Number) s.get("lessonId")).longValue();
        lessons.requireStudent(studentId, lessonId);
        if (studentId.equals(((Number) s.get("studentId")).longValue())) throw new IllegalArgumentException("不能给自己的作品评分");
        if (!Boolean.TRUE.equals(config(((Number) s.get("pageId")).longValue()).get("peerReview"))) {
            throw new IllegalArgumentException("老师未开启互评");
        }
        if (score < 0 || score > 100) throw new IllegalArgumentException("评分须在 0-100 之间");
        jdbc.update("INSERT INTO peer_review(submission_id, reviewer_id, score, comment) VALUES (:s, :r, :sc, :c) "
                        + "ON CONFLICT (submission_id, reviewer_id) DO UPDATE SET score = EXCLUDED.score, comment = EXCLUDED.comment, created_at = now()",
                p("s", submissionId, "r", studentId, "sc", score, "c", settings.filterSensitive(comment)));
    }

    /** 可补交的作品页。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lateList(Long studentId) {
        boolean allowLate = settings.getBool(SystemSettingService.HOMEWORK_ALLOW_LATE);
        // 课后补交（原 afterclassfilelist）：已下课课堂中开放的作品页；系统开放补交时列出未交的，被发回重做的总是列出
        return jdbc.queryForList("SELECT l.id AS \"lessonId\", c.name AS \"courseName\", pg.id AS \"pageId\", pg.title AS \"pageTitle\", "
                + "l.started_at AS \"startedAt\", s.id AS \"submissionId\", s.returned, s.return_note AS \"returnNote\" "
                + "FROM lesson l JOIN users st ON st.class_id = l.class_id JOIN course c ON c.id = l.course_id "
                + "JOIN course_page pg ON pg.course_id = l.course_id AND pg.page_type = 'HOMEWORK' "
                + "LEFT JOIN lesson_page_state ps ON ps.lesson_id = l.id AND ps.page_id = pg.id "
                + "LEFT JOIN submission s ON s.lesson_id = l.id AND s.page_id = pg.id AND s.student_id = st.id "
                + "WHERE st.id = :s AND l.status = 'ENDED' AND COALESCE(ps.is_open, pg.student_visible) "
                + "AND (s.returned OR (:late AND s.id IS NULL)) ORDER BY l.started_at DESC LIMIT 100",
                p("s", studentId, "late", allowLate));
    }

    /** 我的作品（原 stuupfilelist）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> myWorks(Long studentId) {
        return jdbc.queryForList("SELECT " + SUB_COLS + ", c.name AS \"courseName\", pg.title AS \"pageTitle\" "
                + "FROM submission s JOIN users u ON u.id = s.student_id JOIN course_page pg ON pg.id = s.page_id "
                + "JOIN course c ON c.id = pg.course_id WHERE s.student_id = :s ORDER BY s.submitted_at DESC", p("s", studentId));
    }

    /** 精华作品展示（原 Essencestufile，按年级筛选）。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> showcase(Long yearId) {
        return jdbc.queryForList("SELECT s.id, u.name AS \"studentName\", sy.label || k.name AS \"className\", c.name AS \"courseName\", "
                        + "pg.title AS \"pageTitle\", s.file_name AS \"fileName\", s.file_size AS \"fileSize\", s.description, "
                        + "s.teacher_score AS \"teacherScore\", s.title, s.self_score AS \"selfScore\", s.rating, s.returned, s.return_note AS \"returnNote\", s.teacher_comment AS \"teacherComment\", s.submitted_at AS \"submittedAt\" "
                        + "FROM submission s JOIN users u ON u.id = s.student_id JOIN klass k ON k.id = u.class_id "
                        + "JOIN school_year sy ON sy.id = k.school_year_id JOIN course_page pg ON pg.id = s.page_id "
                        + "JOIN course c ON c.id = pg.course_id WHERE s.essence "
                        + (yearId == null ? "" : "AND sy.id = :y ") + "ORDER BY s.submitted_at DESC LIMIT 200",
                p("y", yearId));
    }

    /** 下载权限：本人；课程教师；同课堂同学（开放展示/互评时）；精华作品所有人可看。 */
    @Transactional(readOnly = true)
    public Map<String, Object> downloadable(Long userId, String role, Long submissionId) {
        Map<String, Object> s = submission(submissionId);
        if (Boolean.TRUE.equals(s.get("essence"))) return s;
        if ("TEACHER".equals(role)) {
            lessons.requireTeacher(userId, ((Number) s.get("lessonId")).longValue());
            return s;
        }
        if ("ADMIN".equals(role)) return s;
        if (userId.equals(((Number) s.get("studentId")).longValue())) return s;
        lessons.requireStudent(userId, ((Number) s.get("lessonId")).longValue());
        Map<String, Object> cfg = config(((Number) s.get("pageId")).longValue());
        if (Boolean.TRUE.equals(cfg.get("showClassmates")) || Boolean.TRUE.equals(cfg.get("peerReview"))) return s;
        throw new IllegalArgumentException("无权查看该作品");
    }

    // ================= 教师 =================

    /** 某堂课某作品页：全班学生提交情况（原 markstufile）。 */
    @Transactional(readOnly = true)
    public Map<String, Object> pageSubmissions(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        lessons.requirePageOfLesson(lessonId, pageId);
        Map<String, Object> out = new HashMap<>();
        out.put("config", config(pageId));
        out.put("rubric", rubricItemsFor(pageId));
        out.put("students", jdbc.queryForList(
                "SELECT st.id AS \"studentId\", st.name AS \"studentName\", st.student_no AS \"studentNo\", s.id, s.file_name AS \"fileName\", "
                        + "s.file_size AS \"fileSize\", s.description, s.pc_name AS \"pcName\", s.submitted_at AS \"submittedAt\", "
                        + "s.submit_count AS \"submitCount\", s.late, s.auto_score AS \"autoScore\", s.auto_max AS \"autoMax\", "
                        + "s.auto_detail AS \"autoDetail\", s.teacher_score AS \"teacherScore\", s.rubric_scores AS \"rubricScores\", "
                        + "s.title, s.self_score AS \"selfScore\", s.rating, s.returned, s.return_note AS \"returnNote\", s.teacher_comment AS \"teacherComment\", s.essence, "
                        + "(SELECT ROUND(AVG(pr.score), 1) FROM peer_review pr WHERE pr.submission_id = s.id) AS \"peerAvg\" "
                        + "FROM lesson l JOIN users st ON st.class_id = l.class_id AND st.role = 'STUDENT' AND NOT st.graduated "
                        + "LEFT JOIN submission s ON s.lesson_id = l.id AND s.page_id = :p AND s.student_id = st.id "
                        + "WHERE l.id = :l ORDER BY st.student_no NULLS LAST, st.name", p("l", lessonId, "p", pageId)));
        return out;
    }

    @Transactional
    public void grade(Long teacherId, Long submissionId, Double score, Map<String, Object> rubricScores, String comment) {
        grade(teacherId, submissionId, score, rubricScores, comment, null);
    }

    /** 批阅：分数（或量规）、评语、好评/中评/差评。 */
    @Transactional
    public void grade(Long teacherId, Long submissionId, Double score, Map<String, Object> rubricScores, String comment, String rating) {
        if (rating != null && !rating.isEmpty() && !Arrays.asList("GOOD", "OK", "BAD").contains(rating)) throw new IllegalArgumentException("评价只能是好评/中评/差评");
        Map<String, Object> s = submission(submissionId);
        lessons.requireTeacher(teacherId, ((Number) s.get("lessonId")).longValue());
        String rubricJson = null;
        if (rubricScores != null && !rubricScores.isEmpty()) {
            double sum = 0;
            for (Object v : rubricScores.values()) {
                if (v instanceof Number) sum += ((Number) v).doubleValue();
            }
            score = sum;
            try {
                rubricJson = json.writeValueAsString(rubricScores);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }
        jdbc.update("UPDATE submission SET teacher_score = :sc, rubric_scores = :r, teacher_comment = :c, rating = :ra, graded_at = now() WHERE id = :id",
                p("sc", score, "r", rubricJson, "c", comment, "ra", rating == null || rating.isEmpty() ? null : rating, "id", submissionId));
    }

    /** 发回重做（原 viswtufile“发回重做”）：学生可在课后重新提交，即使系统未开放补交。 */
    @Transactional
    public void returnForRedo(Long teacherId, Long submissionId, String note) {
        Map<String, Object> s = submission(submissionId);
        lessons.requireTeacher(teacherId, ((Number) s.get("lessonId")).longValue());
        if (note != null && note.length() > 200) throw new IllegalArgumentException("说明不超过 200 字");
        jdbc.update("UPDATE submission SET returned = TRUE, return_note = :n WHERE id = :id", p("n", note, "id", submissionId));
    }

    /** 偷懒模式（原 markstufile2）：批量打分、批量评语、批量评价；只填了的项才修改，可选择跳过已批改的。 */
    @Transactional
    public int batchGrade(Long teacherId, List<Long> ids, Double score, String comment, String rating, boolean skipGraded) {
        if (ids.isEmpty()) throw new IllegalArgumentException("请先勾选作品");
        if (rating != null && !rating.isEmpty() && !Arrays.asList("GOOD", "OK", "BAD").contains(rating)) throw new IllegalArgumentException("评价只能是好评/中评/差评");
        int n = 0;
        for (Long id : ids) {
            Map<String, Object> s = submission(id);
            lessons.requireTeacher(teacherId, ((Number) s.get("lessonId")).longValue());
            if (skipGraded && s.get("teacherScore") != null) continue;
            n += jdbc.update("UPDATE submission SET teacher_score = COALESCE(:sc, teacher_score), teacher_comment = COALESCE(:c, teacher_comment), "
                    + "rating = COALESCE(:ra, rating), graded_at = now() WHERE id = :id",
                    p("sc", score, "c", comment == null || comment.isEmpty() ? null : comment, "ra", rating == null || rating.isEmpty() ? null : rating, "id", id));
        }
        return n;
    }

    @Transactional
    public void setEssence(Long teacherId, Long submissionId, boolean essence) {
        Map<String, Object> s = submission(submissionId);
        lessons.requireTeacher(teacherId, ((Number) s.get("lessonId")).longValue());
        jdbc.update("UPDATE submission SET essence = :e WHERE id = :id", p("e", essence, "id", submissionId));
    }

    /** 重新自动评分（修改规则后）：对该页全部作品重算。 */
    @Transactional
    public int regrade(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        int n = 0;
        for (Map<String, Object> s : jdbc.queryForList(
                "SELECT id, file_path, file_name FROM submission WHERE lesson_id = :l AND page_id = :p", p("l", lessonId, "p", pageId))) {
            Map<String, Object> auto = grading.evaluate(pageId, storage.resolve((String) s.get("file_path")), (String) s.get("file_name"));
            jdbc.update("UPDATE submission SET auto_score = :as, auto_max = :am, auto_detail = :ad WHERE id = :id",
                    p("as", auto == null ? null : auto.get("score"), "am", auto == null ? null : auto.get("max"),
                            "ad", auto == null ? null : auto.get("detail"), "id", s.get("id")));
            n++;
        }
        return n;
    }

    /** 打包下载本页全部作品（原 stufilepack），文件名：学号_姓名_原文件名。 */
    @Transactional(readOnly = true)
    public byte[] zip(Long teacherId, Long lessonId, Long pageId) {
        lessons.requireTeacher(teacherId, lessonId);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Set<String> names = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(bos, java.nio.charset.StandardCharsets.UTF_8)) {
            for (Map<String, Object> s : jdbc.queryForList(
                    "SELECT s.file_path, s.file_name, u.name, u.student_no FROM submission s JOIN users u ON u.id = s.student_id "
                            + "WHERE s.lesson_id = :l AND s.page_id = :p ORDER BY u.student_no", p("l", lessonId, "p", pageId))) {
                Path f = storage.resolve((String) s.get("file_path"));
                if (!Files.exists(f)) continue;
                String name = (s.get("student_no") == null ? "" : s.get("student_no") + "_") + s.get("name") + "_" + s.get("file_name");
                while (!names.add(name)) name = "_" + name;
                zos.putNextEntry(new ZipEntry(name));
                Files.copy(f, zos);
                zos.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalStateException("打包失败：" + e.getMessage());
        }
        return bos.toByteArray();
    }

    /** 课堂作品汇总：每个作品页的提交/批阅进度。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> lessonSummary(Long teacherId, Long lessonId) {
        lessons.requireTeacher(teacherId, lessonId);
        return jdbc.queryForList(
                "SELECT pg.id AS \"pageId\", pg.title, pg.score AS \"fullScore\", "
                        + "(SELECT COUNT(*) FROM submission s WHERE s.lesson_id = l.id AND s.page_id = pg.id) AS \"submitted\", "
                        + "(SELECT COUNT(*) FROM submission s WHERE s.lesson_id = l.id AND s.page_id = pg.id AND s.teacher_score IS NOT NULL) AS \"graded\", "
                        + "(SELECT COUNT(*) FROM submission s WHERE s.lesson_id = l.id AND s.page_id = pg.id AND s.essence) AS \"essence\", "
                        + "(SELECT ROUND(AVG(COALESCE(s.teacher_score, s.auto_score)), 1) FROM submission s WHERE s.lesson_id = l.id AND s.page_id = pg.id) AS \"avgScore\", "
                        + "(SELECT COUNT(*) FROM users st WHERE st.class_id = l.class_id AND st.role = 'STUDENT' AND NOT st.graduated) AS \"studentCount\" "
                        + "FROM lesson l JOIN course_page pg ON pg.course_id = l.course_id AND pg.page_type = 'HOMEWORK' "
                        + "WHERE l.id = :l ORDER BY pg.seq", p("l", lessonId));
    }

    /** 作品批阅：教师所有课堂中待批阅的作品页。 */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> pendingForTeacher(Long teacherId) {
        return jdbc.queryForList(
                "SELECT l.id AS \"lessonId\", c.name AS \"courseName\", sy.label || k.name AS \"className\", l.started_at AS \"startedAt\", "
                        + "pg.id AS \"pageId\", pg.title, COUNT(s.id) AS \"submitted\", "
                        + "COUNT(s.id) FILTER (WHERE s.teacher_score IS NULL) AS \"ungraded\" "
                        + "FROM lesson l JOIN course c ON c.id = l.course_id JOIN klass k ON k.id = l.class_id "
                        + "JOIN school_year sy ON sy.id = k.school_year_id "
                        + "JOIN course_page pg ON pg.course_id = l.course_id AND pg.page_type = 'HOMEWORK' "
                        + "LEFT JOIN submission s ON s.lesson_id = l.id AND s.page_id = pg.id "
                        + "WHERE l.teacher_id = :t GROUP BY l.id, c.name, sy.label, k.name, l.started_at, pg.id, pg.title "
                        + "HAVING COUNT(s.id) > 0 ORDER BY l.started_at DESC", p("t", teacherId));
    }

    // ---- helpers ----

    public Map<String, Object> submission(Long id) {
        List<Map<String, Object>> l = jdbc.queryForList("SELECT " + SUB_COLS + ", s.file_path AS \"filePath\" "
                + "FROM submission s JOIN users u ON u.id = s.student_id WHERE s.id = :id", p("id", id));
        if (l.isEmpty()) throw new IllegalArgumentException("作品不存在");
        return l.get(0);
    }

    private List<Map<String, Object>> rubricItemsFor(Long pageId) {
        Object rid = config(pageId).get("rubricId");
        return rid == null ? null : grading.items(((Number) rid).longValue());
    }

    private static String normalizeExts(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder();
        for (String e : s.toLowerCase(Locale.ROOT).split("[,，\\s|]+")) {
            String x = e.replace(".", "").trim();
            if (x.isEmpty()) continue;
            if (b.length() > 0) b.append(',');
            b.append(x);
        }
        return b.toString();
    }
}
